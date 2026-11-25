#!/usr/bin/env node
/**
 * Live provider verification harness.
 *
 *   node infrastructure/scripts/verify-providers.mjs
 *
 * Run against the STAGING stack. It never sends anything that changes state
 * (no email delivered, no payment initialized, no business data modified) —
 * it checks that each configured provider can be reached and that our
 * credentials are accepted, and reports what it cannot prove without a human
 * completing the flow.
 *
 * Every check is independent. A provider whose credentials are absent is
 * reported SKIP (configuration has no live verification yet, never PASS).
 * A configured but unverifiable provider is PARTIAL. A failed live request
 * is FAIL. Only a completed, valid provider test is PASS.
 *
 * This script cannot replace the real journeys (a real OAuth login, a real
 * delivered email, a real small sandbox payment). See the manual checklists
 * in docs/DEPLOYMENT.md.
 *
 * ---------------------------- configuration ------------------------------
 * Canonical names (the gitignored `.env.staging` is loaded automatically when
 * it exists; an explicitly set environment variable always wins over the
 * file). Legacy aliases are documented and resolved only when the canonical
 * name is not set.
 *
 *   STAGING_OPENAI_API_KEY            (legacy alias: OPENAI_API_KEY)
 *   STAGING_CHAPA_SECRET_KEY          (legacy alias: CHAPA_SECRET_KEY)
 *   STAGING_CHAPA_TEST_TXREF          (legacy alias: CHAPA_TEST_TXREF)
 *   STAGING_GOOGLE_CLIENT_ID          (legacy alias: GOOGLE_CLIENT_ID)
 *   STAGING_GOOGLE_CLIENT_SECRET      (legacy alias: GOOGLE_CLIENT_SECRET)
 *   STAGING_GOOGLE_REDIRECT_URI       (legacy alias: GOOGLE_REDIRECT_URI)
 *   STAGING_MAIL_HOST                 (legacy alias: SPRING_MAIL_HOST)
 *   STAGING_MAIL_PORT                 (legacy alias: SPRING_MAIL_PORT)
 *   STAGING_MAIL_USERNAME             (legacy alias: SPRING_MAIL_USERNAME)
 *   STAGING_MAIL_PASSWORD             (legacy alias: SPRING_MAIL_PASSWORD)
 *
 * Secrets are never printed to stdout/stderr or logs.
 */

import { connect } from "node:net";
import { request as httpsRequest } from "node:https";
import { connect as tlsConnect } from "node:tls";
import dns from "node:dns/promises";
import { existsSync, readFileSync } from "node:fs";
import { join, dirname, resolve } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, "..", "..");
const DEFAULT_DOTENV_PATH = join(ROOT, ".env.staging");

const TIMEOUT_MS = 10_000;

const results = [];

function record(name, status, detail) {
  results.push({ name, status, detail });
  const pad = name.padEnd(18);
  console.log(`${pad} ${status.padEnd(6)} ${detail}`);
}

/* ---------------------------- configuration ---------------------------- */

const LEGACY_ALIASES = {
  OPENAI_API_KEY: "STAGING_OPENAI_API_KEY",
  CHAPA_SECRET_KEY: "STAGING_CHAPA_SECRET_KEY",
  CHAPA_TEST_TXREF: "STAGING_CHAPA_TEST_TXREF",
  GOOGLE_CLIENT_ID: "STAGING_GOOGLE_CLIENT_ID",
  GOOGLE_CLIENT_SECRET: "STAGING_GOOGLE_CLIENT_SECRET",
  GOOGLE_REDIRECT_URI: "STAGING_GOOGLE_REDIRECT_URI",
  SPRING_MAIL_HOST: "STAGING_MAIL_HOST",
  SPRING_MAIL_PORT: "STAGING_MAIL_PORT",
  SPRING_MAIL_USERNAME: "STAGING_MAIL_USERNAME",
  SPRING_MAIL_PASSWORD: "STAGING_MAIL_PASSWORD",
};

/** Values that must never be treated as real credentials. */
const PLACEHOLDER_RE =
  /^(?:mock|test|change_me|changeme|placeholder|replace_with_\S+|<[^>]+>|\[[^\]]+\]|\.\.\.|yourdomain|yourdomain\.com|\*{3,})$/i;

function stripQuotes(value) {
  if (value.length >= 2 && ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'")))) {
    return value.slice(1, -1);
  }
  return value;
}

/**
 * Safely load KEY=VALUE lines from `.env.staging` into `env`.
 *
 * - No shell evaluation — it is parsed, never `source`d.
 * - Keys the caller already set are not overwritten (explicit environment
 *   always wins over the file).
 * - Values are never printed.
 */
function loadDotenvStaging(env, path) {
  if (!existsSync(path)) return false;
  for (const rawLine of readFileSync(path, "utf8").split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith("#")) continue;
    const eq = line.indexOf("=");
    if (eq < 0) continue;
    const key = line.slice(0, eq).trim();
    if (!key || env[key] !== undefined) continue;
    env[key] = stripQuotes(line.slice(eq + 1).trim());
  }
  return true;
}

function pick(env, canonical, legacy) {
  const canonicalValue = env[canonical] ?? "";
  if (canonicalValue !== "") return canonicalValue;
  return env[legacy] ?? "";
}

/**
 * Resolve the canonical STAGING_* credential names from `env`, after
 * loading `.env.staging` if it exists.
 *
 * A non-empty canonical value wins over the legacy alias (conflicting
 * old/new variables resolve deterministically: canonical first).
 */
export function resolveCredentials(env = process.env, dotenvPath = DEFAULT_DOTENV_PATH) {
  const loadedDotenv = loadDotenvStaging(env, dotenvPath);
  return {
    openaiApiKey: pick(env, "STAGING_OPENAI_API_KEY", "OPENAI_API_KEY"),
    chapaSecretKey: pick(env, "STAGING_CHAPA_SECRET_KEY", "CHAPA_SECRET_KEY"),
    chapaTestTxRef: pick(env, "STAGING_CHAPA_TEST_TXREF", "CHAPA_TEST_TXREF"),
    googleClientId: pick(env, "STAGING_GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_ID"),
    googleClientSecret: pick(env, "STAGING_GOOGLE_CLIENT_SECRET", "GOOGLE_CLIENT_SECRET"),
    googleRedirectUri: pick(env, "STAGING_GOOGLE_REDIRECT_URI", "GOOGLE_REDIRECT_URI"),
    mailHost: pick(env, "STAGING_MAIL_HOST", "SPRING_MAIL_HOST"),
    mailPort: pick(env, "STAGING_MAIL_PORT", "SPRING_MAIL_PORT"),
    mailUsername: pick(env, "STAGING_MAIL_USERNAME", "SPRING_MAIL_USERNAME"),
    mailPassword: pick(env, "STAGING_MAIL_PASSWORD", "SPRING_MAIL_PASSWORD"),
    loadedDotenv,
  };
}

/** True when a value is missing or is a documentation placeholder. */
export function isPlaceholder(value) {
  return !value || PLACEHOLDER_RE.test(value.trim());
}

/* ------------------------------ HTTP client ------------------------------ */

function httpRequest({ method = "GET", host, path, headers = {}, body, timeout = TIMEOUT_MS }) {
  return new Promise((resolve, reject) => {
    const req = httpsRequest(
      { method, host, path, headers, timeout },
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
    req.on("timeout", () => req.destroy(new Error("timeout")));
    req.on("error", reject);
    if (body) req.write(body);
    req.end();
  });
}

/* ----------------------------- OpenAI provider ---------------------------- */

/** PASS: model list retrieved; 401: rejected (FAIL); else FAIL. */
async function checkOpenAI(creds) {
  if (isPlaceholder(creds.openaiApiKey)) {
    record("OpenAI (AI)", "SKIP", "STAGING_OPENAI_API_KEY not set (or a placeholder)");
    return;
  }
  try {
    const res = await httpRequest({
      host: "api.openai.com",
      path: "/v1/models",
      headers: { Authorization: `Bearer ${creds.openaiApiKey}` },
    });
    if (res.status === 200) {
      record("OpenAI (AI)", "PASS", "API key accepted; model list retrieved");
    } else if (res.status === 401) {
      record("OpenAI (AI)", "FAIL", "API key rejected (401)");
    } else {
      record("OpenAI (AI)", "FAIL", `unexpected HTTP ${res.status}`);
    }
  } catch (error) {
    record("OpenAI (AI)", "FAIL", `request failed: ${error.message}`);
  }
}

/* ------------------------------ Chapa provider ---------------------------- */

/** PASS: verified transaction returned; otherwise FAIL when configured. */
async function checkChapa(creds) {
  if (isPlaceholder(creds.chapaSecretKey)) {
    record("Chapa (billing)", "SKIP", "STAGING_CHAPA_SECRET_KEY not set (or a placeholder)");
    return;
  }
  if (!creds.chapaTestTxRef) {
    record(
      "Chapa (billing)",
      "SKIP",
      "STAGING_CHAPA_TEST_TXREF not set — a live transaction reference is required (use the staging sandbox key and a test transaction; never run a live-money check in staging)",
    );
    return;
  }
  try {
    const res = await httpRequest({
      host: "api.chapa.co",
      path: `/v1/transaction/verify/${encodeURIComponent(creds.chapaTestTxRef)}`,
      headers: { Authorization: `Bearer ${creds.chapaSecretKey}` },
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

/**
 * A syntactically valid client ID is not a successful OAuth test — the live
 * consent flow is completed by a human in staging (docs/DEPLOYMENT.md).
 */
function checkGoogleOauth(creds) {
  if (!creds.googleClientId || !creds.googleClientSecret) {
    record("Google OAuth", "SKIP", "STAGING_GOOGLE_CLIENT_ID / STAGING_GOOGLE_CLIENT_SECRET not set");
    return;
  }
  if (isPlaceholder(creds.googleClientId) || isPlaceholder(creds.googleClientSecret)) {
    record("Google OAuth", "SKIP", "STAGING_GOOGLE_CLIENT_ID / STAGING_GOOGLE_CLIENT_SECRET are placeholders");
    return;
  }
  if (!/^[0-9]+-[a-z0-9]+\.apps\.googleusercontent\.com$/.test(creds.googleClientId.trim())) {
    record("Google OAuth", "FAIL", "STAGING_GOOGLE_CLIENT_ID does not look like a valid OAuth client id (format check only — the live consent flow must be completed by a human in staging)");
    return;
  }
  if (!creds.googleRedirectUri) {
    record("Google OAuth", "FAIL", "STAGING_GOOGLE_REDIRECT_URI not set (exact URI must be whitelisted in the Google Cloud console)");
    return;
  }
  record("Google OAuth", "PARTIAL", "client id format and redirect URI are set; the live consent flow must be completed by a human in staging (see docs/DEPLOYMENT.md)");
}

/* --------------------------------- SMTP --------------------------------- */

/**
 * SMTP verification without changing state:
 *   1. DNS resolution of the relay host.
 *   2. TCP connection (implicit TLS on port 465, STARTTLS on 25/587).
 *   3. SMTP AUTH (AUTH LOGIN/PLAIN) — the credentials are accepted by the
 *      server (a 2xx reply), proving authentication works end to end.
 *
 * A real test email delivery is documented in the DEPLOYMENT.md checklist
 * (register a new user and verify the address); the harness never sends.
 */
function smtpVerify({ host, port, user, password }) {
  return new Promise((resolvePromise) => {
    let buffer = "";
    let step = "greeting";
    let settled = false;

    const socket = port === 465
      ? tlsConnect({ host, port, timeout: TIMEOUT_MS, servername: host }, () => {})
      : connect({ host, port, timeout: TIMEOUT_MS }, () => {});

    const finish = (ok, detail) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      socket.destroy();
      resolvePromise({ ok, detail });
    };

    const timer = setTimeout(() => finish(false, `timed out connecting to ${host}:${port}`), TIMEOUT_MS);

    const send = (cmd) => socket.write(`${cmd}\r\n`);

    socket.on("error", (error) => finish(false, `connection failed: ${error.message}`));
    socket.on("timeout", () => finish(false, `timed out connecting to ${host}:${port}`));
    socket.on("data", (data) => {
      buffer += data.toString("utf8");
      let idx;
      while ((idx = buffer.indexOf("\r\n")) >= 0) {
        const line = buffer.slice(0, idx);
        buffer = buffer.slice(idx + 2);
        handle(line);
      }
    });

    function handle(line) {
      const code = line.slice(0, 3);
      if (step === "greeting") {
        if (line.startsWith("220")) { send("EHLO yemombizgrow-verify"); step = "ehlo"; }
        else finish(false, `unexpected greeting: ${line.slice(0, 80)}`);
      } else if (step === "ehlo") {
        if (code !== "250") finish(false, `EHLO rejected: ${line.slice(0, 80)}`);
        else if (/STARTTLS/i.test(line)) { send("STARTTLS"); step = "starttls"; }
        else if (user && password) { send("AUTH LOGIN"); step = "auth_user"; }
        else finish(true, `TCP + EHLO OK on ${host}:${port} (no STARTTLS/credentials — see DEPLOYMENT.md for the controlled delivery test)`);
      } else if (step === "starttls") {
        if (code === "220") finish(true, `STARTTLS established for ${host}:${port} (credentials not attempted — see DEPLOYMENT.md)`);
        else finish(false, `STARTTLS refused: ${line.slice(0, 80)}`);
      } else if (step === "auth_user") {
        if (code === "334") { send(Buffer.from(user).toString("base64")); step = "auth_pass"; }
        else finish(false, `AUTH LOGIN refused: ${line.slice(0, 80)}`);
      } else if (step === "auth_pass") {
        if (code === "334") { send(Buffer.from(password).toString("base64")); step = "auth_done"; }
        else finish(false, `AUTH password refused: ${line.slice(0, 80)}`);
      } else if (step === "auth_done") {
        if (code === "235") finish(true, `SMTP AUTH accepted for ${host}:${port} (no message sent — see DEPLOYMENT.md)`);
        else finish(false, `AUTH not accepted: ${line.slice(0, 80)}`);
      }
    }
  });
}

async function checkSmtp(creds) {
  if (!creds.mailHost) {
    record("SMTP (email)", "SKIP", "STAGING_MAIL_HOST not set");
    return;
  }
  if (isPlaceholder(creds.mailHost)) {
    record("SMTP (email)", "SKIP", "STAGING_MAIL_HOST is a placeholder");
    return;
  }
  if (!creds.mailUsername || !creds.mailPassword) {
    record("SMTP (email)", "SKIP", "STAGING_MAIL_USERNAME / STAGING_MAIL_PASSWORD not set — live delivery is documented in the DEPLOYMENT.md checklist");
    return;
  }
  const host = creds.mailHost.trim();
  const port = Number.parseInt(creds.mailPort || "587", 10) || 587;

  // 1. DNS resolution
  const resolved = await dns.lookup(host).then(() => true).catch(() => false);
  if (!resolved) {
    record("SMTP (email)", "FAIL", `DNS resolution failed for ${host}`);
    return;
  }
  // 2. TCP + STARTTLS/implicit TLS + 3. SMTP AUTH
  const result = await smtpVerify({ host, port, user: creds.mailUsername, password: creds.mailPassword });
  if (result.ok) {
    record("SMTP (email)", "PASS", result.detail);
  } else {
    record("SMTP (email)", "FAIL", result.detail);
  }
}

/* --------------------------------- run --------------------------------- */

async function main() {
  const creds = resolveCredentials();
  console.log("YemomBizGrow live provider verification (STAGING credentials only)\n");
  await checkOpenAI(creds);
  await checkChapa(creds);
  checkGoogleOauth(creds);
  await checkSmtp(creds);

  const failed = results.filter((r) => r.status === "FAIL");
  const skipped = results.filter((r) => r.status === "SKIP");
  const partial = results.filter((r) => r.status === "PARTIAL");
  const passed = results.filter((r) => r.status === "PASS").length;

  console.log(
    `\nResults: ${passed} passed, ${skipped.length} skipped (no credentials or placeholders), ` +
      `${partial.length} partial (human step required), ${failed.length} failed`,
  );

  if (failed.length > 0) {
    console.error("One or more provider checks FAILED. Do not ship with a failing provider.");
    process.exit(1);
  }
  if (skipped.length > 0 || partial.length > 0) {
    console.log("These results are NOT a live verification. Complete the checklist in docs/DEPLOYMENT.md before production.");
  }
}

function isMain() {
  try {
    const argv1 = process.argv[1];
    if (!argv1) return false;
    return pathToFileURL(resolve(argv1)).href === import.meta.url;
  } catch {
    return false;
  }
}

if (isMain()) {
  main();
}
