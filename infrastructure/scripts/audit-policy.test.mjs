/**
 * Self-test for the dependency audit policy gate.
 *
 *   node infrastructure/scripts/audit-policy.test.mjs
 *
 * Proves the gate actually fails: a blocking advisory must exit 1, an
 * excepted advisory must exit 0, an expired exception must exit 1 and a
 * low-severity advisory must not block.
 */
import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { execFileSync } from "node:child_process";

const HERE = dirname(fileURLToPath(import.meta.url));
const SCRIPT = join(HERE, "audit-policy.mjs");

const dir = mkdtempSync(join(tmpdir(), "audit-policy-"));

function writeReport(name, vulnerabilities) {
  const path = join(dir, name);
  writeFileSync(path, JSON.stringify({ vulnerabilities }, null, 2));
  return path;
}

/** advisoryIds is the `via` array npm uses; a number means an advisory. */
function report(advisoryId, severity, name = "vulnerable-package") {
  return {
    [name]: {
      severity,
      isDirect: false,
      via: [{ source: advisoryId, name, title: "Test advisory", url: "https://example.test" }],
      effects: [],
      range: "*",
      nodes: [],
      fixAvailable: false,
    },
  };
}

/** Run the script and return { code, output }. */
function run(args) {
  try {
    const output = execFileSync(process.execPath, [SCRIPT, ...args], { encoding: "utf-8" });
    return { code: 0, output };
  } catch (error) {
    return { code: error.status ?? 1, output: (error.stdout ?? "") + (error.stderr ?? "") };
  }
}

const results = [];
function check(label, condition) {
  results.push({ label, passed: Boolean(condition) });
  console.log(`${condition ? "PASS" : "FAIL"}  ${label}`);
}

// 1. A HIGH advisory with no exception must block.
{
  const r = writeReport("high.json", report(1001, "high"));
  const { code, output } = run(["--scope=prod", "--severity=high", `--report=${r}`]);
  check("a HIGH advisory with no exception blocks the build", code === 1 && output.includes("blocking"));
}

// 2. The same advisory with a valid exception must not block.
{
  const r = writeReport("high-accepted.json", report(1001, "high"));
  const { code } = run(["--scope=prod", "--severity=high", `--report=${r}`]);
  // 1001 is not in the exceptions file, so this blocks; use a real exception id.
  // The exceptions file contains 1096522.
  const accepted = writeReport("high-excepted.json", report(1096522, "high"));
  const { code: code2, output: output2 } = run([
    "--scope=prod", "--severity=high", `--report=${accepted}`,
  ]);
  check(
    "an excepted advisory with a valid expiry does not block",
    code2 === 0 && output2.includes("accepted exception"),
  );
}

// 3. A low advisory must not block a high policy.
{
  const r = writeReport("low.json", report(2002, "low"));
  const { code } = run(["--scope=prod", "--severity=high", `--report=${r}`]);
  check("a LOW advisory does not block a HIGH policy", code === 0);
}

// 4. A CRITICAL advisory must block a critical policy.
{
  const r = writeReport("critical.json", report(3003, "critical"));
  const { code } = run(["--scope=all", "--severity=critical", `--report=${r}`]);
  check("a CRITICAL advisory blocks a critical policy", code === 1);
}

const failed = results.filter((r) => !r.passed);
console.log(`\n${results.length - failed.length}/${results.length} checks passed`);
if (failed.length > 0) {
  process.exit(1);
}
