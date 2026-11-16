/**
 * Minimal load harness. No external dependencies.
 *
 *   node infrastructure/scripts/load-test.mjs [--url=URL] [--requests=N] [--concurrency=C] [--method=GET|POST] [--body=JSON] [--max-error-rate=0.01]
 *
 * Measures wall-clock throughput, latency percentiles and the error rate for
 * a given endpoint. Intended for the local stack or a staging deployment.
 *
 * Defaults hit the backend readiness probe (a cheap, read-only endpoint). To
 * exercise the real request path, point it at a cheap public endpoint or use
 * a dedicated test user for authenticated endpoints.
 */

const args = Object.fromEntries(
  process.argv.slice(2).map((arg) => {
    const match = /^--([\w-]+)=(.*)$/.exec(arg);
    return match ? [match[1], match[2]] : [arg, true];
  }),
);

const url = args.url ?? "http://localhost:8080/actuator/health/readiness";
const totalRequests = Number.parseInt(args.requests ?? "200", 10);
const concurrency = Number.parseInt(args.concurrency ?? "20", 10);
const method = (args.method ?? "GET").toUpperCase();
const body = args.body ?? null;
const maxErrorRate = Number.parseFloat(args["max-error-rate"] ?? "0.01");

const stats = {
  completed: 0,
  failed: 0,
  latencies: [],
  byStatus: new Map(),
};

function percentile(sorted, fraction) {
  const index = Math.ceil(sorted.length * fraction) - 1;
  return sorted[Math.max(0, index)];
}

const headers = { Accept: "application/json" };
if (body) headers["Content-Type"] = "application/json";

async function worker(start, end, onDone) {
  for (let i = start; i < end; i++) {
    const begin = performance.now();
    let status = 0;
    try {
      const response = await fetch(url, { method, headers, body });
      status = response.status;
    } catch {
      status = 0; // network/timeout failure: counted as a failure below
    }
    const elapsed = performance.now() - begin;
    stats.latencies.push(elapsed);
    stats.byStatus.set(status, (stats.byStatus.get(status) ?? 0) + 1);
    if (status === 0 || status >= 500) stats.failed += 1;
    stats.completed += 1;
    onDone?.();
  }
}

console.log(`load-test: ${method} ${url}`);
console.log(`requests=${totalRequests} concurrency=${concurrency}`);

const deadline = setTimeout(() => {
  console.error("load-test: timed out after 120s");
  process.exit(2);
}, 120_000);

const started = performance.now();
const chunkSize = Math.ceil(totalRequests / concurrency);
const workers = [];
for (let c = 0; c < concurrency; c++) {
  const start = c * chunkSize;
  const end = Math.min(totalRequests, start + chunkSize);
  if (start < end) workers.push(worker(start, end, null));
}
await Promise.all(workers);
const elapsed = performance.now() - started;
clearTimeout(deadline);

stats.latencies.sort((a, b) => a - b);
const completed = stats.latencies.length;
const mean = stats.latencies.reduce((sum, value) => sum + value, 0) / completed;
const errorRate = stats.failed / totalRequests;

console.log("\n--- result ---");
console.log(`requests      : ${totalRequests}`);
console.log(`completed     : ${completed}`);
console.log(`failed (5xx/err): ${stats.failed}  (${(errorRate * 100).toFixed(2)}%)`);
console.log(`throughput    : ${(completed / (elapsed / 1000)).toFixed(1)} req/s`);
console.log(`latency mean  : ${mean.toFixed(1)} ms`);
console.log(`latency p50   : ${percentile(stats.latencies, 0.5)?.toFixed(1)} ms`);
console.log(`latency p95   : ${percentile(stats.latencies, 0.95)?.toFixed(1)} ms`);
console.log(`latency p99   : ${percentile(stats.latencies, 0.99)?.toFixed(1)} ms`);
console.log(`latency max   : ${stats.latencies[stats.latencies.length - 1].toFixed(1)} ms`);
console.log("status codes  :", Object.fromEntries(stats.byStatus));

if (errorRate > maxErrorRate) {
  console.error(`\nFAILED: error rate ${(errorRate * 100).toFixed(2)}% exceeds the ${(maxErrorRate * 100).toFixed(2)}% threshold`);
  process.exit(1);
}
console.log("\nOK: load test within thresholds");
