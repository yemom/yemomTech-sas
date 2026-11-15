/**
 * Dependency audit policy gate.
 *
 *   node infrastructure/scripts/audit-policy.mjs [--scope=prod|all] [--severity=high|critical]
 *
 * The policy, in one place:
 *
 *   * PRODUCTION dependencies  -> HIGH and CRITICAL advisories BLOCK the build.
 *   * DEVELOPMENT dependencies -> only CRITICAL advisories block the build.
 *   * An advisory may only be excepted with a documented reason and an expiry
 *     date. Expired exceptions block the build until they are renewed.
 *
 * Exceptions live in `.github/dependency-exceptions.json`:
 *
 *   {
 *     "1096522": {
 *       "reason": "Upstream advisory in a build-time-only package; no runtime impact.",
 *       "expires": "2026-06-30"
 *   }
 * }
 *
 * `npm audit` exits non-zero when it finds anything, so it cannot be used to
 * decide the outcome directly - the exit code is ignored on purpose and the
 * JSON report is evaluated against the policy above instead.
 */
import { readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { execFileSync } from "node:child_process";

const REPO_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..", "..");
const FRONTEND_DIR = join(REPO_ROOT, "frontend");
const EXCEPTIONS_FILE = join(REPO_ROOT, ".github", "dependency-exceptions.json");

function parseArgs(argv) {
  const options = { scope: "prod", severity: "high", report: null };
  for (const arg of argv) {
    const match = /^--(\w+)=(.+)$/.exec(arg);
    if (match) options[match[1]] = match[2];
  }
  return options;
}

const options = parseArgs(process.argv.slice(2));

/** npm audit severity ordering. */
const SEVERITY_RANK = { info: 0, low: 1, moderate: 2, high: 3, critical: 4 };

function thresholdFor(scope, severity) {
  if (scope === "all") {
    // Development dependencies: only critical blocks.
    return severity === "critical" ? SEVERITY_RANK.critical : SEVERITY_RANK.critical;
  }
  return SEVERITY_RANK[severity] ?? SEVERITY_RANK.high;
}

function loadExceptions() {
  try {
    const parsed = JSON.parse(readFileSync(EXCEPTIONS_FILE, "utf-8"));
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch (error) {
    if (error.code === "ENOENT") {
      return {};
    }
    throw new Error(`Could not read ${EXCEPTIONS_FILE}: ${error.message}`);
  }
}

function npmCommand() {
  // `npm` is `npm.cmd` on Windows, and the PATH is not always consulted by
  // spawnSync. Prefer the npm that invoked this process, then the platform
  // default.
  if (process.env.npm_execpath) {
    return process.env.npm_execpath.endsWith(".js")
      ? [process.execPath, process.env.npm_execpath]
      : [process.env.npm_execpath];
  }
  return [process.platform === "win32" ? "npm.cmd" : "npm"];
}

const exceptions = loadExceptions();
const threshold = thresholdFor(options.scope, options.severity);

console.log(
  `[audit-policy] scope=${options.scope} blocking-from=${Object.keys(SEVERITY_RANK).find((k) => SEVERITY_RANK[k] === threshold)}`,
);

let report;
if (options.report) {
  // Test hook: evaluate a synthetic report instead of running npm. Used by
  // the self-test to prove that the gate fails on a blocking advisory.
  try {
    report = JSON.parse(readFileSync(options.report, "utf-8"));
  } catch (error) {
    console.error(`[audit-policy] could not read the report ${options.report}: ${error.message}`);
    process.exit(2);
  }
} else {
  const [npmBin, ...npmPrefix] = npmCommand();
  const auditArgs = [...npmPrefix, ...(options.scope === "all"
    ? ["audit", "--json"]
    : ["audit", "--omit=dev", "--json"])];

  try {
    // The exit code is intentionally ignored: npm audit returns non-zero as soon
    // as it finds anything, and the policy decides the outcome, not npm.
    report = JSON.parse(
      execFileSync(npmBin, auditArgs, {
        cwd: FRONTEND_DIR,
        encoding: "utf-8",
        stdio: ["ignore", "pipe", "pipe"],
        // Node refuses to spawn a .cmd/.bat without a shell on Windows.
        shell: process.platform === "win32",
      }),
    );
  } catch (error) {
    if (error.stdout) {
      try {
        report = JSON.parse(error.stdout);
      } catch {
        console.error(`[audit-policy] could not parse the npm audit report: ${error.message}`);
        process.exit(2);
      }
    } else {
      console.error(`[audit-policy] npm audit could not run: ${error.message}`);
      process.exit(2);
    }
  }
}

const advisories = report.vulnerabilities ?? {};
const today = new Date().toISOString().slice(0, 10);

const blocking = [];
const accepted = [];
const expired = [];

for (const [name, advisory] of Object.entries(advisories)) {
  const severity = advisory.severity ?? "info";
  const rank = SEVERITY_RANK[severity] ?? 0;
  const via = advisory.via ?? [];
  const advisoryIds = [
    ...new Set(
      via
        .filter((entry) => typeof entry === "object" && entry.source !== undefined)
        .map((entry) => String(entry.source)),
    ),
  ];
  const direct = via.filter((entry) => typeof entry === "string");

  if (rank < threshold) {
    continue;
  }

  const exception = advisoryIds
    .map((id) => exceptions[id])
    .find(Boolean);

  if (!exception) {
    blocking.push({ name, severity, advisoryIds, direct, url: advisory.url });
    continue;
  }

  if (!exception.reason || !exception.expires) {
    blocking.push({
      name,
      severity,
      advisoryIds,
      direct,
      url: advisory.url,
      note: "the exception is missing a reason or an expiry date",
    });
    continue;
  }

  if (exception.expires < today) {
    expired.push({ name, severity, advisoryIds, expires: exception.expires, reason: exception.reason });
    continue;
  }

  accepted.push({ name, severity, advisoryIds, expires: exception.expires, reason: exception.reason });
}

if (accepted.length > 0) {
  console.log(`[audit-policy] ${accepted.length} accepted exception(s):`);
  for (const item of accepted) {
    console.log(`  - ${item.name} (${item.severity}) until ${item.expires}: ${item.reason}`);
  }
}

if (expired.length > 0) {
  console.error(`[audit-policy] ${expired.length} EXPIRED exception(s) — renew or fix:`);
  for (const item of expired) {
    console.error(`  - ${item.name} (${item.severity}) expired ${item.expires}: ${item.reason}`);
  }
}

if (blocking.length > 0) {
  console.error(`[audit-policy] ${blocking.length} blocking vulnerabilit(ies):`);
  for (const item of blocking) {
    const ids = item.advisoryIds.length > 0 ? item.advisoryIds.join(", ") : item.direct.join(", ");
    console.error(`  - ${item.name} (${item.severity}) ${ids} ${item.url ?? ""}`);
    if (item.note) console.error(`      ${item.note}`);
  }
  console.error(
    "[audit-policy] FAILED. Fix the dependency, or record a documented, dated exception in " +
      ".github/dependency-exceptions.json.",
  );
  process.exit(1);
}

if (expired.length > 0) {
  console.error("[audit-policy] FAILED: expired exceptions must be renewed or the dependency fixed.");
  process.exit(1);
}

console.log("[audit-policy] OK: no blocking vulnerabilities.");
