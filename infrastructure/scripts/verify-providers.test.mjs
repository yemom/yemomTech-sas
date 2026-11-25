/**
 * Self-test for the verify-providers.mjs environment resolution.
 *
 *   node infrastructure/scripts/verify-providers.test.mjs
 *
 * Covers:
 * - all credentials missing
 * - canonical STAGING_* names present
 * - legacy aliases used when the canonical name is unset
 * - canonical wins over conflicting legacy variables
 * - placeholder detection (mock/test/change_me/etc., empty string)
 * - real values are not placeholders
 * - safe .env.staging loading (explicit env wins; file values apply;
 *   missing file is fine)
 * - secret values never appear in the script's output
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { isPlaceholder, resolveCredentials } from "./verify-providers.mjs";

const HERE = dirname(fileURLToPath(import.meta.url));
const SCRIPT = join(HERE, "verify-providers.mjs");

const results = [];
function check(label, condition, extra) {
  results.push({ label, passed: Boolean(condition) });
  console.log(`${condition ? "PASS" : "FAIL"}  ${label}${extra ? " — " + extra : ""}`);
}

const CANONICAL = [
  "STAGING_OPENAI_API_KEY",
  "STAGING_CHAPA_SECRET_KEY",
  "STAGING_CHAPA_TEST_TXREF",
  "STAGING_GOOGLE_CLIENT_ID",
  "STAGING_GOOGLE_CLIENT_SECRET",
  "STAGING_GOOGLE_REDIRECT_URI",
  "STAGING_MAIL_HOST",
  "STAGING_MAIL_PORT",
  "STAGING_MAIL_USERNAME",
  "STAGING_MAIL_PASSWORD",
];
const LEGACY = [
  "OPENAI_API_KEY",
  "CHAPA_SECRET_KEY",
  "CHAPA_TEST_TXREF",
  "GOOGLE_CLIENT_ID",
  "GOOGLE_CLIENT_SECRET",
  "GOOGLE_REDIRECT_URI",
  "SPRING_MAIL_HOST",
  "SPRING_MAIL_PORT",
  "SPRING_MAIL_USERNAME",
  "SPRING_MAIL_PASSWORD",
];

const EMPTY_PATH = "/nonexistent/.env.staging";

function clear(env) {
  for (const key of [...CANONICAL, ...LEGACY]) delete env[key];
}

// 1. All credentials missing.
{
  const env = {};
  clear(env);
  const c = resolveCredentials(env, EMPTY_PATH);
  check(
    "all credentials missing resolve as empty",
    !c.openaiApiKey && !c.chapaSecretKey && !c.chapaTestTxRef &&
      !c.googleClientId && !c.googleClientSecret && !c.googleRedirectUri &&
      !c.mailHost && !c.mailUsername && !c.mailPassword,
  );
}

// 2. Canonical STAGING_* names are resolved.
{
  const env = {};
  clear(env);
  env.STAGING_OPENAI_API_KEY = "sk-real-key-123";
  env.STAGING_GOOGLE_CLIENT_ID = "123456789-abc.apps.googleusercontent.com";
  const c = resolveCredentials(env, EMPTY_PATH);
  check("canonical STAGING_OPENAI_API_KEY resolved", c.openaiApiKey === "sk-real-key-123");
  check(
    "canonical STAGING_GOOGLE_CLIENT_ID resolved",
    c.googleClientId === "123456789-abc.apps.googleusercontent.com",
  );
}

// 3. Legacy aliases are used when the canonical name is unset.
{
  const env = {};
  clear(env);
  env.OPENAI_API_KEY = "legacy-key";
  env.SPRING_MAIL_HOST = "smtp.example.com";
  const c = resolveCredentials(env, EMPTY_PATH);
  check("legacy alias OPENAI_API_KEY resolves to openaiApiKey", c.openaiApiKey === "legacy-key");
  check("legacy alias SPRING_MAIL_HOST resolves to mailHost", c.mailHost === "smtp.example.com");
}

// 4. Canonical wins over a conflicting legacy variable.
{
  const env = {};
  clear(env);
  env.STAGING_OPENAI_API_KEY = "canonical";
  env.OPENAI_API_KEY = "legacy";
  const c = resolveCredentials(env, EMPTY_PATH);
  check("canonical wins over conflicting legacy value", c.openaiApiKey === "canonical");
}

// 5. Placeholder detection.
{
  check("placeholder 'change_me' detected", isPlaceholder("change_me"));
  check("placeholder 'test' detected", isPlaceholder("test"));
  check("placeholder 'mock' detected", isPlaceholder("mock"));
  check("placeholder 'change_me' uppercase detected", isPlaceholder("CHANGE_ME"));
  check("placeholder 'replace_with_xxx' detected", isPlaceholder("replace_with_xxx"));
  check("placeholder '<your-key>' detected", isPlaceholder("<your-key>"));
  check("placeholder '[password]' detected", isPlaceholder("[password]"));
  check("placeholder '***' detected", isPlaceholder("****"));
  check("empty string detected as placeholder", isPlaceholder(""));
  check("real key 'sk-real-123' not a placeholder", !isPlaceholder("sk-real-123"));
  check(
    "real client id '123456789-abc.apps.googleusercontent.com' not a placeholder",
    !isPlaceholder("123456789-abc.apps.googleusercontent.com"),
  );
}

// 6. Safe .env.staging loading.
{
  const dir = mkdtempSync(join(tmpdir(), "vp-test-"));
  const envFile = join(dir, ".env.staging");
  writeFileSync(
    envFile,
    "STAGING_OPENAI_API_KEY=from-file\nSTAGING_MAIL_HOST=smtp.from-file.example\nSTAGING_MAIL_USERNAME=file-user\n# a comment line\n",
  );

  const env = {};
  clear(env);
  env.STAGING_OPENAI_API_KEY = "explicit"; // canonical set → wins
  const c = resolveCredentials(env, envFile);
  check(".env.staging values are loaded when env keys are unset", c.mailHost === "smtp.from-file.example");
  check("explicit env wins over .env.staging", c.openaiApiKey === "explicit");
  check(".env.staging value applies when env key is unset", c.mailUsername === "file-user");
  check(".env.staging loaded when the file exists", c.loadedDotenv === true);

  rmSync(dir, { recursive: true, force: true });

  const missingFileEnv = {};
  clear(missingFileEnv);
  const c2 = resolveCredentials(missingFileEnv, "/nonexistent/.env.staging");
  check(".env.staging missing → still resolves (no crash)", c2.mailHost === "" && c2.loadedDotenv === false);
}

// 7. Secrets never appear in the script's output.
{
  const secret = "TOP-SECRET-VALUE-DO-NOT-PRINT-12345";
  const dir = mkdtempSync(join(tmpdir(), "vp-secret-"));
  const envFile = join(dir, ".env.staging");
  writeFileSync(envFile, `STAGING_OPENAI_API_KEY=${secret}\n`);

  const env = { ...process.env, STAGING_OPENAI_API_KEY: secret };
  clear(env);
  env.STAGING_OPENAI_API_KEY = secret;
  const result = spawnSync(process.execPath, [SCRIPT], {
    encoding: "utf8",
    env,
    timeout: 60000,
    maxBuffer: 10 * 1024 * 1024,
  });
  const output = `${result.stdout ?? ""}${result.stderr ?? ""}`;
  check("script completes (fake key → live 401 or network error)", result.status !== null, `status=${result.status}`);
  check("secret value is never printed by the script", !output.includes(secret));
  check("script reports OpenAI check line", output.includes("OpenAI (AI)"));
  rmSync(dir, { recursive: true, force: true });
}

const failed = results.filter((r) => !r.passed);
if (failed.length > 0) process.exitCode = 1;
console.log(`\nverify-providers.test.mjs: ${results.length - failed.length}/${results.length} passed`);
