import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // The API base URL is the only runtime configuration the browser needs;
  // it must be a NEXT_PUBLIC_* variable (bundled, never a secret).
  env: {
    NEXT_PUBLIC_API_BASE_URL:
      process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8082/api/v1",
  },
  // Keep the refresh cookie working: the browser sends it cross-origin only
  // when fetch opts in with credentials, and CORS must echo the exact origin.
  async headers() {
    return [];
  },
};

export default nextConfig;
