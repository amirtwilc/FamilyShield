# FamilyShield backend (Next.js 16) container.
# Build once, then run: db-init (idempotent schema) -> next start.
FROM node:20-bookworm-slim AS build
WORKDIR /app
# Build tools for native deps (argon2).
RUN apt-get update && apt-get install -y --no-install-recommends python3 make g++ \
    && rm -rf /var/lib/apt/lists/*
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:20-bookworm-slim AS runner
WORKDIR /app
ENV NODE_ENV=production
COPY --from=build /app ./
EXPOSE 3000
# Apply schema if missing, then start the server. Honor the platform's $PORT
# (Railway/Render inject it); fall back to 3000 locally.
CMD ["sh", "-c", "node scripts/db-init.mjs && npx next start -H 0.0.0.0 -p ${PORT:-3000}"]
