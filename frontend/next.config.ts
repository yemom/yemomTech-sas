import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Enable standalone output for Docker deployment (copies only needed files)
  output: "standalone",
  // The API base URL is a NEXT_PUBLIC_* variable and is inlined into the
  // browser bundle at build time straight from the build environment — no
  // `env` block is needed, and none may declare a fallback: a production
  // build without NEXT_PUBLIC_API_BASE_URL must fail loudly (it does, in
  // src/lib/api/api-base.ts), never silently bake localhost into a deployed
  // bundle. Development gets the localhost fallback with a console warning
  // from the same module.
  // Keep the refresh cookie working: the browser sends it cross-origin only
  // when fetch opts in with credentials, and CORS must echo the exact origin.
  async headers() {
    return [];
  },
};

export default nextConfig;
