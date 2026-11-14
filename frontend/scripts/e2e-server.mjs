/**
 * Serves the frontend for E2E tests using the SAME artifact production uses:
 * the Next.js standalone server (`node .next/standalone/server.js`).
 *
 * Why not `next start`? `output: "standalone"` is required for the Docker
 * image, and Next.js explicitly warns that `next start` is not the supported
 * entry point for that output. Running the standalone server here means the
 * browser tests exercise the exact bundle that ships to production.
 *
 * The standalone output does not include the static assets or `public/`, so
 * this script copies them in (exactly like infrastructure/docker/frontend.Dockerfile
 * does) before starting the server.
 */
import { spawn } from "node:child_process";
import { cpSync, existsSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const FRONTEND_ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const NEXT_DIR = join(FRONTEND_ROOT, ".next");
const STANDALONE_DIR = join(NEXT_DIR, "standalone");

function fail(message) {
  console.error(`[e2e-server] ${message}`);
  process.exit(1);
}

if (!existsSync(join(STANDALONE_DIR, "server.js"))) {
  fail(
    "No standalone build found at .next/standalone/server.js.\n" +
      "            Run `npm run build` first (or use `npm run e2e`, which builds first).",
  );
}

// Static assets and public files are not part of the standalone output.
const staticSource = join(NEXT_DIR, "static");
if (existsSync(staticSource)) {
  cpSync(staticSource, join(STANDALONE_DIR, ".next", "static"), { recursive: true });
}
const publicSource = join(FRONTEND_ROOT, "public");
if (existsSync(publicSource)) {
  cpSync(publicSource, join(STANDALONE_DIR, "public"), { recursive: true });
}

const port = process.env.E2E_PORT ?? "3101";
const host = process.env.E2E_HOST ?? "127.0.0.1";

const server = spawn(process.execPath, [join(STANDALONE_DIR, "server.js")], {
  cwd: STANDALONE_DIR,
  stdio: "inherit",
  env: { ...process.env, PORT: port, HOSTNAME: host },
});

server.on("exit", (code) => process.exit(code ?? 0));

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => server.kill(signal));
}
