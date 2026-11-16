/**
 * Live provider verification harness.
 *
 *   node infrastructure/scripts/verify-providers.mjs
 *
 * Run this against a STAGING deployment with STAGING credentials. It never
 * sends anything that changes state (no email is delivered to an arbitrary
 * address, no payment is initialized) — it checks that each configured
 * provider can be reached and that our credentials are accepted, and it
 * reports what it cannot prove without a human completing the flow.
 *
 * Every check is independent. A provider whose credentials are absent is
 * reported SKIP, not PASS.
 *
 * This script cannot replace a human completing the real journey in staging
 * (a real OAuth login, a real email delivered, a real small payment). See
 * "Live provider verification" in docs/DEPLOYMENT.md.
 */
import { request } from "node:https";
import { connect } from "node:net";

const TIMEOUT_MS = 10_000;

const results = [];

function record(name, status, detail) {
  results.push({ name, status, detail });
  const pad = name.padEnd(18);
  console.log(`${pad} ${status.padEnd(6)} ${detail}`);
}

function httpRequest({ method = "GET", host, path, headers = {}, body, timeout = TIMEOUT_MS }) {
  return new Promise((resolve, reject) => {
    const req = request(
      {
        method,
        host,
        path,
        headers,
        timeout,
      },
      (res) => {
        const chunks = [];
        res.on("data", (chunk) => chunks.push(chunk));
        res.on("end", () => resolve({
          status: res.statusCode,
          headers: res.headers,
          body: Buffer.concat(chunks).toString("utf-8"),
        }));
      },
    );
    req.on("timeout", () => {
      req.destroy(new Error("timeout"));
    });
    req.on("error", reject);
    if (body) req.write(body);
    req.end();
  });
}

/* ----------------------------- OpenAI provider ---------------------------- */

async function checkOpenAI() {
  const key = process.env.OPENAI_API_KEY;
  if (!key || key.trim() === "" || /^(mock|test|change_me)$/i.test(key.trim())) {
    record("OpenAI (AI)", "SKIP", "OPENAI_API_KEY not set (or a placeholder)");
    return;
  }
  try {
    const res = await httpRequest({
      host: "api.openai.com",
      path: "/v1/models",
      headers: { Authorization: `Bearer ${key.trim()}` },
    });
    if (res.status === 200) {
      record("OpenAI (AI)", "PASS", "API key accepted; model list retrieved");
    } else if (res.status === 401) {
      record("OpenAI (AI)", "FAIL", "API key rejected (401)");
    } else {
      record("OpenAI (AI)", "FAIL", `unexpected HTTP ${res.status}: ${res.body.slice(0, 200)}`);
    }
  } catch (error) {
    record("OpenAI (AI)", "FAIL", `request failed: ${error.message}`);
  }
}

/* ------------------------------ Chapa provider ---------------------------- */

async function checkChapa() {
  const secret = process.env.CHAPA_SECRET_KEY;
  const txRef = process.env.CHAPA_TEST_TXREF;
  if (!secret || secret.trim() === "" || /^(mock|test|change_me)$/i.test(secret.trim())) {
    record("Chapa (billing)", "SKIP", "CHAPA_SECRET_KEY not set (or a placeholder)");
    return;
  }
  if (!txRef) {
    record(
      "Chapa (billing)",
      "SKIP",
      "CHAPA_TEST_TXREF not set; cannot verify a live transaction without one (use the staging sandbox key and a test transaction)",
    );
    return;
  }
  try {
    const res = await httpRequest({
      host: "api.chapa.co",
      path: `/v1/transaction/verify/${encodeURIComponent(txRef)}`,
      headers: { Authorization: `Bearer ${secret.trim()}` },
    });
    if (res.status === 200 && /"success"/i.test(res.body)) {
      record("Chapa (billing)", "PASS", "Chapa accepted the secret key and returned a verified transaction");
    } else {
      record("Chapa (billing)", "FAIL", `unexpected HTTP ${res.status}: ${res.body.slice(0, 200)}`);
    }
  } catch (error) {
    record("Chapa (billing)", "FAIL", `request failed: ${error.message}`);
  }
}

/* ------------------------- Google OAuth (client) -------------------------- */

async function checkGoogleOauth() {
  const clientId = process.env.GOOGLE_CLIENT_ID;
  const clientSecret = process.env.GOOGLE_CLIENT_SECRET;
  const redirectUri = process.env.GOOGLE_REDIRECT_URI;
  if (!clientId || !clientSecret) {
    record("Google OAuth", "SKIP", "GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET not set");
    return;
  }
  if (!/^[0-9]+-[a-z0-9]+\.apps\.googleusercontent\.com$/.test(clientId.trim())) {
    record("Google OAuth", "FAIL", "GOOGLE_CLIENT_ID does not look like a valid OAuth client id");
    return;
  }
  if (!redirectUri) {
    record("Google OAuth", "FAIL", "GOOGLE_REDIRECT_URI not set");
    return;
  }
  record(
    "Google OAuth",
    "PARTIAL",
    "client id format and redirect URI are set; the live consent flow must be completed by a human in staging (see DEPLOYMENT.md)",
  );
}

/* --------------------------------- SMTP --------------------------------- */

function probeTcp(host, port, secure) {
  return new Promise((resolve) => {
    const socket = connect({ host, port, timeout: TIMEOUT_MS });
    let settled = false;
    const finish = (ok, detail) => {
      if (settled) return;
      settled = true;
      socket.destroy();
      resolve({ ok, detail });
    };
    socket.once("connect", () => finish(true, `connected to ${host}:${port}`));
    socket.once("timeout", () => finish(false, `timed out connecting to ${host}:${port}`));
    socket.once("error", (error) => finish(false, `connection failed: ${error.message}`));
  });
}

async function checkSmtp() {
  const host = process.env.SPRING_MAIL_HOST;
  const port = Number.parseInt(process.env.SPRING_MAIL_PORT ?? "587", 10);
  const user = process.env.SPRING_MAIL_USERNAME;
  const password = process.env.SPRING_MAIL_PASSWORD;
  if (!host) {
    record("SMTP (email)", "SKIP", "SPRING_MAIL_HOST not set");
    return;
  }
  if (!user || !password) {
    record("SMTP (email)", "SKIP", "SPRING_MAIL_USERNAME / SPRING_MAIL_PASSWORD not set");
    return;
  }
  const tcp = await probeTcp(host, port);
  if (!tcp.ok) {
    record("SMTP (email)", "FAIL", tcp.detail);
    return;
  }
  record(
    "SMTP (email)",
    "PARTIAL",
    `${tcp.detail}; credentials present — a real email must be sent in staging to confirm delivery (see DEPLOYMENT.md)`,
  );
}

/* --------------------------------- run --------------------------------- */

console.log("YemomBizGrow live provider verification (STAGING credentials only)\n");
await checkOpenAI();
await checkChapa();
await checkGoogleOauth();
await checkSmtp();

const failed = results.filter((r) => r.status === "FAIL");
const skipped = results.filter((r) => r.status === "SKIP");
const partial = results.filter((r) => r.status === "PARTIAL");

console.log(
  `\n${results.length - failed.length}/${results.length} checks usable; ` +
    `${skipped.length} skipped (no credentials), ${partial.length} partial (human step required)`,
);

if (failed.length > 0) {
  console.error("One or more provider checks FAILED. Do not ship with a failing provider.");
  process.exit(1);
}
if (skipped.length > 0 || partial.length > 0) {
  console.log("These results are NOT a live verification. Complete the checklist in docs/DEPLOYMENT.md before production.");
}
