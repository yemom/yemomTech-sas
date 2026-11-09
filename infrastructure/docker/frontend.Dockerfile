# =============================================================
# YemomBizGrow Frontend — multi-stage Docker build (Next.js standalone)
# Stage 1: install dependencies
# Stage 2: build the Next.js app
# Stage 3: lean Node 20 Alpine runtime (standalone output)
# =============================================================

# ---- Stage 1: deps ----
FROM node:20-alpine AS deps

RUN apk add --no-cache libc6-compat

WORKDIR /app

COPY frontend/package.json frontend/package-lock.json ./

RUN npm ci

# ---- Stage 2: builder ----
FROM node:20-alpine AS builder

WORKDIR /app

# Copy node_modules from deps stage so we can typecheck too
COPY --from=deps /app/node_modules ./node_modules
COPY frontend/ .

# Build-time env vars (not secrets — bundled into browser JS)
ARG NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api/v1
ENV NEXT_PUBLIC_API_BASE_URL=$NEXT_PUBLIC_API_BASE_URL

# Enable standalone output so the runtime stage is self-contained
ENV NEXT_TELEMETRY_DISABLED=1

RUN npm run build

# ---- Stage 3: runtime ----
FROM node:20-alpine AS runtime

RUN apk add --no-cache libc6-compat

# Non-root user
RUN addgroup -S nodejs && adduser -S nextjs -G nodejs

WORKDIR /app

ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

# Copy the standalone Next.js server and static assets
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static    ./.next/static
COPY --from=builder --chown=nextjs:nodejs /app/public          ./public

HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=10 \
  CMD wget -qO- http://localhost:3000/ || exit 1

USER nextjs

EXPOSE 3000

CMD ["node", "server.js"]
