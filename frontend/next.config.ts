import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Enable standalone output for Docker deployment (copies only needed files)
  output: "standalone",
  // The API base URL is the only runtime configuration the browser needs;
  // it must be a NEXT_PUBLIC_* variable (bundled, never a secret).
  //
  // The fallback MUST match the fallback in src/lib/api/client.ts and the
  // local backend port documented in docker-compose.yml (8080). A mismatched
  // fallback produces a frontend that boots cleanly but talks to the wrong
  // backend — the exact "confusing behaviour" this value used to cause
  // (it pointed at 8082 while everything else used 8080).
  env: {
    NEXT_PUBLIC_API_BASE_URL:
      process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1",
  },
  // Keep the refresh cookie working: the browser sends it cross-origin only
  // when fetch opts in with credentials, and CORS must echo the exact origin.
  async headers() {
    return [];
  },
};

export default nextConfig;
