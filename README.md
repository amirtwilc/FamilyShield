# FamilyShield — Backend & Data Core

The backend service for FamilyShield, a parental-safety app. This is **slice 1** of the larger system: the REST API and data core that the native Android parent/child apps (later slices) consume. It handles parent accounts, child-device pairing, encrypted-in-transit location ingestion, history, device status, and low-battery / offline push alerts.

> 📖 **For a full product & feature overview** (both apps, every feature, screens, architecture, how to run) **see [`FEATURES.md`](FEATURES.md).**

> Design spec: `docs/superpowers/specs/2026-06-19-familyshield-backend-data-core-design.md`
> Implementation plan: `docs/superpowers/plans/2026-06-19-familyshield-backend-data-core.md`

Clients:
- **Native Android app** (`android/`) — the primary client: a Parent dashboard and a
  Kid-device simulator in Kotlin + Jetpack Compose + Material 3, OpenStreetMap maps.
  See [`android/README.md`](android/README.md).
- **Web client** (same Next.js app) — a browser Parent dashboard + Kid simulator,
  handy for quick API exercising. See [Web client](#web-client).

## Stack

Next.js 16 (App Router, Node runtime) · TypeScript · Drizzle ORM + `pg` (node-postgres) · PostgreSQL + **PostGIS** · Zod · `jose` (JWT) · `argon2` · `firebase-admin` (FCM) · `@asteasolutions/zod-to-openapi` + Swagger UI · Vitest.

## Prerequisites

- Node.js 20+ and npm
- Docker Desktop (for the local PostGIS database)

## Setup

```bash
# 1. Install dependencies
npm install

# 2. Configure environment
cp .env.example .env        # local defaults already target the docker DB on :5433

# 3. Start PostGIS (postgis/postgis:16-3.4 on host port 5433)
docker compose up -d

# 4. Create the dedicated test database (one-time)
docker exec familyshield-db psql -U familyshield -d familyshield \
  -c "CREATE DATABASE familyshield_test;"
```

The default `.env` points `DATABASE_URL` at `familyshield` and `TEST_DATABASE_URL` at `familyshield_test`, both on `localhost:5433`.

## Database migrations

Drizzle generates the baseline schema; the `locations` table is **range-partitioned by month** and is created by a hand-written migration.

```bash
npm run db:generate        # generate/refresh drizzle baseline migration from src/db/schema.ts
npm run db:migrate         # apply drizzle-tracked migrations for simple local iteration
npm run db:prod:setup      # production bootstrap: PostGIS + every drizzle/*.sql once
npm run db:check           # verify Neon/PostGIS/tier/partition readiness
```

> **IMPORTANT (production deploy):** `drizzle-kit migrate` applies only drizzle-tracked migrations (`0000_*`, `0002_*`, …). The partition migration **`drizzle/0001_locations_partition.sql` is intentionally NOT in drizzle's journal** (it drops the generated `locations` table and recreates it `PARTITION BY RANGE (recorded_at)` with a composite PK and a GiST geom index). You must apply it **manually** after `db:migrate`, e.g.:
> ```bash
> psql "$DATABASE_URL" -f drizzle/0001_locations_partition.sql
> ```
> The test harness (`resetDb`) applies every `drizzle/*.sql` in sorted order, so tests handle this automatically. Monthly partitions thereafter are created on demand by `ensureLocationPartition()` during ingestion.

For **local development**, `npm run db:setup` applies the full schema (postgis extension + every `drizzle/*.sql`, including the partition override) to the `DATABASE_URL` database in one step — use this to prepare the dev DB before `npm run dev`.

For a fresh **Neon production** database, prefer `npm run db:prod:setup`. It enables PostGIS, applies every SQL migration in order, records them in `familyshield_migrations`, and refuses to baseline an already-populated DB unless `ALLOW_BASELINE_EXISTING=1` is set after manual verification. Run `npm run db:check` afterward.

## Running

```bash
npm run dev                # start the API on http://localhost:3000
npm test                   # run the full Vitest suite (needs the test DB up)
npm run test:watch         # watch mode
npx tsc --noEmit           # type-check
npm run build              # production build (compiles all routes)
```

## Web client

A browser front end lives in the same Next.js app (white/blue palette, MapLibre +
OpenStreetMap tiles — never Google Maps). Routes:

| Route | Purpose |
|---|---|
| `/` | Landing page linking to both apps |
| `/parent` | Parent dashboard: register/login → children → live location map, online/battery status, alerts, pairing-code generation |
| `/kid` | Kid-device test client: pair with a code, view monitors, and manually send test telemetry |

The simulator → backend → dashboard loop is what the E2E test exercises.

```bash
npm run db:setup           # one-time: prepare the dev DB schema
npm run dev                # http://localhost:3000  → open /parent and /kid
npm run test:e2e           # Playwright E2E: parent + kid full loop
```

> Design spec: `docs/superpowers/specs/2026-06-20-familyshield-web-client-design.md`

## API documentation (Swagger)

With the dev server running:

- **Swagger UI:** http://localhost:3000/api/docs
- **OpenAPI 3.1 JSON:** http://localhost:3000/api/openapi.json

The spec is generated from the same Zod schemas used for request validation, so docs and validation never drift.

## Authentication

Two audiences:

| Audience | Mechanism | Header |
|---|---|---|
| **Parent** | Email + password → JWT access (15 m) + refresh (30 d) | `Authorization: Bearer <accessToken>` |
| **Child device** | Opaque device token issued at pairing (stored only as a SHA-256 hash) | `Authorization: Bearer <deviceToken>` |

Every parent route authorizes by `parent_id` ownership; device routes by device token. No cross-tenant access.

## Endpoints

**Device-facing** (device-token auth):

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/pair` | Exchange a 6-digit pairing code for a device token |
| POST | `/api/locations` | Batch-upload location points once per paired kid device (idempotent on `device_id` + `recorded_at`) |
| POST | `/api/device/status` | Heartbeat: battery, charging, FCM token |
| GET | `/api/device/monitoring` | Kid-visible monitor list |
| DELETE | `/api/device/monitors/{parentId}` | Kid removes one monitoring parent |
| GET / POST | `/api/device/monitors/{parentId}/messages` | Kid chat with one specific parent |

**Parent-facing** (parent JWT):

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/auth/register` · `/api/auth/login` · `/api/auth/refresh` | Account + tokens |
| POST / GET | `/api/children` | Create / list children (with devices + status) |
| GET | `/api/children/{id}` | Child detail |
| POST | `/api/children/{id}/pairing-code` | Generate a pairing code |
| GET | `/api/children/{id}/location/current` | Latest location |
| GET | `/api/children/{id}/location/history?date=YYYY-MM-DD&cursor=` | Day timeline (cursor-paginated) |
| GET | `/api/children/{id}/alerts?cursor=` | Alerts (cursor-paginated) |
| POST / GET / DELETE | `/api/children/{id}/zones` · `/zones/{zoneId}` | Safe-zone CRUD (storage only) |
| DELETE | `/api/devices/{id}` | Revoke a device |
| POST | `/api/parent/push-token` | Register the parent's FCM token |

**Internal:** `GET /api/cron/offline-sweep` — fires offline alerts for stale devices; guarded by `Authorization: Bearer $CRON_SECRET`, scheduled via `vercel.json` cron.

Retention cleanup also runs through `GET /api/cron/location-retention`, guarded by the same cron secret and scheduled hourly in `vercel.json`.

## Alerts

- **Low battery** — fires when an ingested battery level ≤ `LOW_BATTERY_THRESHOLD` and not charging; debounced per device by `LOW_BATTERY_COOLDOWN_MIN`.
- **Offline** — the cron sweep fires once per device whose `last_seen_at` exceeds `OFFLINE_THRESHOLD_MIN` with no open offline alert.

Both persist to `alerts` (readable via `GET …/alerts`) and push via FCM to the parent's registered token.

## Configuration

| Var | Purpose | Default |
|---|---|---|
| `DATABASE_URL` / `TEST_DATABASE_URL` | Postgres connection strings | docker on `:5433` |
| `JWT_SECRET` / `JWT_REFRESH_SECRET` | Token signing keys | — |
| `FCM_SERVICE_ACCOUNT_JSON` | Firebase service-account JSON (push). Empty = pushes no-op | — |
| `LOW_BATTERY_THRESHOLD` | Low-battery % trigger | 15 |
| `LOW_BATTERY_COOLDOWN_MIN` | Low-battery debounce window | 60 |
| `OFFLINE_THRESHOLD_MIN` | Offline detection threshold | 30 |
| `PAIRING_CODE_TTL_MIN` | Pairing-code lifetime | 10 |
| `MAX_LOCATION_BATCH` | Max points per upload | 200 |
| `CRON_SECRET` | Bearer secret for the cron endpoint | — |

## Tiers and retention

- `subscription_tiers` stores tier limits. The seeded `free` tier keeps 2 days of location history and allows 5 monitored children per parent.
- Location rows are stored once per kid device and shared with every linked parent through `child_parent_links`.
- If a child has parents on different tiers later, retention uses the longest `location_retention_days` among linked parents.
- `GET /api/cron/location-retention` deletes expired location rows and clears stale `devices.last_location`; current/history reads also filter out expired points before cleanup runs.

## Vercel + Neon

1. Create a Neon Postgres database and enable pooled/TCP access for Vercel.
2. Set Vercel env vars: `DATABASE_URL`, `JWT_SECRET`, `JWT_REFRESH_SECRET`, `CRON_SECRET`, `FCM_SERVICE_ACCOUNT_JSON`, thresholds, and `PG_POOL_MAX=5`.
3. Run `npm run db:prod:setup` against the Neon `DATABASE_URL`.
4. Run `npm run db:check`.
5. Deploy to Vercel; `vercel.json` schedules offline detection every 5 minutes and retention hourly.

## Privacy & security

- TLS in transit (Vercel-terminated); Neon/Postgres managed at-rest encryption. Coordinates are stored as queryable PostGIS geometry (not app-encrypted) so server-side geofencing/ML (later slices) can run.
- Passwords argon2-hashed; device tokens stored only as SHA-256 hashes; pairing codes single-use and TTL-bounded.
- Rate limiting on auth + pairing endpoints.

## Scale notes

- `locations` is month-partitioned; current location is denormalized onto `devices` for O(1) reads; history/current reads are retention-filtered; history/alerts use cursor (keyset) pagination; location uploads are idempotent for safe retry.
- **Before horizontal scale-out**, replace the in-memory rate limiter (`src/lib/ratelimit.ts`) with a Redis/Upstash-backed implementation behind the same `RateLimiter` interface (the in-memory one is per-instance).

## Out of scope (later slices)

Native Android parent/child apps · geofence enter/leave/dwell firing · ML pattern recognition (clustering, place labels, routine deviation) · realtime location streaming. UI slices use a **white/blue palette** and **MapLibre/OpenStreetMap** (not Google Maps), per project constraints.
