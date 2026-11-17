# =============================================================
# YemomBizGrow Frontend — multi-stage Docker build (Next.js standalone)
# Stage 1: install dependencies
# Stage 2: build the Next.js app
# Stage 3: lean Node 20 Alpine runtime (standalone output)
# =============================================================

# ---- Stage 1: deps ----
FROM node:22-alpine AS deps

RUN apk add --no-cache libc6-compat

WORKDIR /app

COPY frontend/package.json frontend/package-lock.json ./

RUN npm ci --prefer-offline

# ---- Stage 2: builder ----
FROM node:22-alpine AS builder

WORKDIR /app

# Copy node_modules from deps stage
COPY --from=deps /app/node_modules ./node_modules
COPY frontend/ .

# Build-time env var (not a secret — it is bundled into browser JS by Next).
# It is REQUIRED: NEXT_PUBLIC_* values are baked into the bundle at build
# time, so a production image without a real backend URL cannot be repaired
# by setting the variable at runtime. An empty value fails the build here
# with an explicit message, before npm run build gets a chance to produce a
# silently-broken frontend. The local compose stack passes the localhost
# default itself (see docker-compose.yml).
ARG NEXT_PUBLIC_API_BASE_URL=
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL

RUN if [ -z "$NEXT_PUBLIC_API_BASE_URL" ]; then \
      echo >&2 "ERROR: the NEXT_PUBLIC_API_BASE_URL build argument is required."; \
      echo >&2 "  Production: docker build --build-arg NEXT_PUBLIC_API_BASE_URL=https://<backend-host>/api/v1 ..."; \
      echo >&2 "  Local compose: it passes http://localhost:8080/api/v1 automatically."; \
      echo >&2 "  The value is baked into the JS bundle at build time — rebuild the image after changing it."; \
      exit 1; \
    fi

# Disable telemetry and enable standalone output
ENV NEXT_TELEMETRY_DISABLED=1

RUN npm run build

# Ensure public/ directory always exists so runtime COPY never fails
# (Docker BuildKit fails if the source path is absent or only .gitkeep)
RUN mkdir -p /app/public

# ---- Stage 3: runtime ----
FROM node:22-alpine AS runtime

RUN apk add --no-cache libc6-compat

# Non-root user
RUN addgroup -S nodejs && adduser -S nextjs -G nodejs

WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

# Copy the standalone Next.js server
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
# Copy static assets
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
# Copy public assets (directory is guaranteed to exist from builder RUN mkdir -p above)
COPY --from=builder --chown=nextjs:nodejs /app/public ./public

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=10 \
  CMD wget -qO- http://127.0.0.1:3000/ || exit 1

USER nextjs

EXPOSE 3000

CMD ["node", "server.js"]
