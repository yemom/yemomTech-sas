import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  reactStrictMode: true,
  // Enable standalone output for Docker deployment (copies only needed files)
  output: "standalone",
  // In development, Turbopack blocks cross-origin dev resources (such as /_next/hmr)
  // from non-localhost origins unless explicitly allowed. Adding the local LAN IP
  // enables testing from phones and LAN devices without breaking client hydration.
  allowedDevOrigins: [
    "localhost",
    "localhost:3001",
    "127.0.0.1",
    "127.0.0.1:3001",
    "192.168.1.26",
    "192.168.1.26:3001",
    "10.50.58.156",
    "10.50.58.156:3001",
    "192.168.137.1",
    "192.168.137.1:3001",
    "172.31.160.1",
    "172.31.160.1:3001",
  ],
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
