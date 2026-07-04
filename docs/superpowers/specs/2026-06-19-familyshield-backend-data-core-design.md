# FamilyShield — Backend & Data Core (Slice 1) — Design

**Date:** 2026-06-19
**Status:** Approved design, pre-implementation
**Scope:** This document specifies **slice 1 of the FamilyShield system: the backend and data core.** It is one of several independent slices. Other slices (native apps, geofence-alert engine, ML pattern recognition) get their own spec → plan → build cycles and are explicitly out of scope here.

---

## 1. Background

FamilyShield is a parental-safety system: a child's Android device reports its location to a backend, and a parent monitors location, history, device status, and safety alerts. The full product is large and decomposes into independent subsystems. We are building the foundation first — the backend everything else depends on — because it is the common dependency and is fully buildable/testable in the current environment.

### Decomposition of the full product (for context)

1. **Backend & data core** — *this slice.*
2. Child tracking app (native Android: background GPS, reboot persistence, battery optimization, permissions, encrypted upload).
3. Parent dashboard app (native Android + map UI).
4. Geofence alert engine (zone enter/leave/dwell firing).
5. ML pattern recognition (GPS clustering, place detection, auto-labeling, recurring routes, routine-deviation alerts).

Each later slice consumes this slice's REST contract.

---

## 2. Cross-cutting constraints (apply to all UI slices, recorded here so they are not lost)

These do **not** affect this backend slice (no UI/map here) but govern every UI-bearing slice:

- **Color system:** white background + blue primary/accent. Clean white-blue theme across the parent dashboard and both native apps.
- **Maps: open-source only — NOT Google Maps.** Native Android → **MapLibre Native** with **OpenStreetMap** vector tiles. Web (if any) → **MapLibre GL JS** (or Leaflet). No Google Maps SDK or API key anywhere. This overrides the original product brief that named "Google Maps SDK for Android." Tile hosting (public demo vs MapTiler free tier vs self-hosted `tileserver-gl`) is decided in the map-UI slice.
- **Localization:** the parent dashboard UI defaults to **Hebrew (עברית)** per standing user preference, with the layout RTL-ready.

---

## 3. Architecture

A single Next.js 16 (App Router) TypeScript service deployed on Vercel, backed by Neon Postgres with the **PostGIS** extension. Push notifications go out via **Firebase Cloud Messaging (FCM)** through the `firebase-admin` SDK. This slice ships **no native app code** — it exposes a REST + OpenAPI contract that future native Kotlin apps consume.

### Stack

| Concern | Choice |
|---|---|
| Runtime / framework | Next.js 16 App Router, TypeScript, Node.js runtime (Fluid Compute) |
| Database | Neon Postgres + PostGIS extension |
| DB access | Drizzle ORM + Neon serverless driver; raw SQL for PostGIS geometry/spatial queries |
| Validation | Zod |
| API docs | `@asteasolutions/zod-to-openapi` → OpenAPI 3.1 → Swagger UI |
| Auth (parents) | Email + password (`argon2` hash) → JWT access/refresh (`jose`) |
| Auth (devices) | Long-lived opaque device token, stored only as SHA-256 hash |
| Push | `firebase-admin` (FCM) |
| Scheduled jobs | Vercel Cron |
| Tests | Vitest (unit + integration against real test Postgres) |

### Two auth audiences

- **Parents** authenticate with email + password and receive a short-lived JWT access token + longer-lived refresh token. All parent endpoints require a valid parent JWT and authorize by `parent_id` ownership.
- **Child devices** authenticate with an opaque device token issued at pairing time, sent as `Authorization: Bearer <device_token>`. Only the SHA-256 hash is stored; tokens are revocable per device.

---

## 4. Data model

PostGIS enabled (`CREATE EXTENSION postgis`). Tables:

### `parents`
`id` (uuid pk) · `email` (citext, unique) · `password_hash` · `created_at` · `updated_at`

### `children`
`id` (uuid pk) · `parent_id` (fk → parents) · `display_name` · `created_at`
Index: `(parent_id)`.

### `devices`
`id` (uuid pk) · `child_id` (fk → children) · `device_token_hash` (unique) · `platform` · `model` · `paired_at` · `revoked_at` (nullable) · `last_seen_at` · `battery_level` (int 0–100) · `is_charging` (bool) · `fcm_token` (nullable) · **`last_location`** (geometry(Point,4326), denormalized for O(1) current-location reads) · `last_location_at`
Index: `(child_id)`, partial index `WHERE revoked_at IS NULL`.

### `pairing_codes`
`id` (uuid pk) · `child_id` (fk) · `code` (6-digit) · `expires_at` (default now()+10min) · `consumed_at` (nullable) · `created_at`
Index: `(code) WHERE consumed_at IS NULL`. Rate-limited generation.

### `locations` — **partitioned by month on `recorded_at`** (hot write path)
`id` (uuid) · `device_id` (fk → devices) · `geom` (geometry(Point,4326)) · `speed` (real, nullable) · `accuracy` (real, nullable) · `battery_level` (int, nullable) · `recorded_at` (timestamptz) · `created_at`
Indexes: composite `(device_id, recorded_at DESC)`; GiST spatial index on `geom` (for future geofencing/ML).
Uniqueness/idempotency: unique `(device_id, recorded_at)` to dedupe re-uploaded batches.

### `safe_zones` — storage only this slice (no firing)
`id` (uuid pk) · `child_id` (fk) · `name` · `center` (geometry(Point,4326)) · `radius_m` (int) · `notify_on_enter` (bool) · `notify_on_exit` (bool) · `dwell_minutes` (int, nullable) · `created_at`

### `alerts`
`id` (uuid pk) · `child_id` (fk) · `device_id` (fk, nullable) · `type` (enum: `low_battery` | `offline`) · `payload` (jsonb) · `created_at` · `delivered_at` (nullable) · `read_at` (nullable)
Index: `(child_id, created_at DESC)`.

---

## 5. Auth & pairing flows

### Parent registration / login
1. `POST /api/auth/register` `{ email, password }` → creates `parents` row (argon2 hash) → returns `{ accessToken, refreshToken }`.
2. `POST /api/auth/login` `{ email, password }` → verifies → returns tokens.
3. `POST /api/auth/refresh` `{ refreshToken }` → new access token.

### Device pairing
1. Parent calls `POST /api/children/:id/pairing-code` → server creates a 6-digit code, 10-minute TTL, returns it (parent reads it to the child / displays it).
2. Child app calls `POST /api/pair` `{ code, platform, model }` → server validates an unconsumed, unexpired code, marks it consumed, creates a `devices` row, generates an opaque device token, stores its hash, returns `{ deviceToken, childId }`.
3. Child app stores the device token securely and uses it as a Bearer token for all subsequent device calls.
4. Parent can revoke a device (`DELETE /api/devices/:id` → sets `revoked_at`), invalidating its token.

---

## 6. API surface (this slice)

All requests/responses JSON. All inputs Zod-validated. Full schemas published as OpenAPI/Swagger (§9).

### Device-facing (device-token auth)
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/pair` | Exchange pairing code for a device token |
| POST | `/api/locations` | **Batch** upload of location points; idempotent by `(device_id, recorded_at)` |
| POST | `/api/device/status` | Heartbeat: `battery_level`, `is_charging`, `fcm_token`; updates `last_seen_at` |

`POST /api/locations` body: `{ points: [{ lat, lng, recorded_at, speed?, accuracy?, battery_level? }] }` (max `MAX_LOCATION_BATCH` points/request, default 200). On insert, the most recent point also updates `devices.last_location` / `last_location_at`. Low-battery alert evaluated here.

### Parent-facing (parent JWT)
| Method | Path | Purpose |
|---|---|---|
| POST | `/api/children` | Create a child profile |
| GET | `/api/children` | List children with their devices + status |
| GET | `/api/children/:id` | Child detail |
| POST | `/api/children/:id/pairing-code` | Generate a pairing code |
| GET | `/api/children/:id/location/current` | Latest location (reads denormalized `last_location`) |
| GET | `/api/children/:id/location/history?date=YYYY-MM-DD&cursor=` | Day timeline, cursor-paginated |
| GET | `/api/children/:id/alerts?cursor=` | Alerts, cursor-paginated |
| POST/GET/PATCH/DELETE | `/api/children/:id/zones` | Safe-zone CRUD (storage only) |
| DELETE | `/api/devices/:id` | Revoke a device |
| POST | `/api/parent/push-token` | Register the parent's FCM token (to receive alerts) |

Every parent endpoint authorizes by verifying the resource's `parent_id` matches the JWT subject (no cross-tenant access).

---

## 7. Encryption & privacy

- **In transit:** HTTPS/TLS for all traffic (Vercel-terminated).
- **At rest:** Neon's managed disk encryption (default). Coordinate columns are **not** app-level AES-encrypted, because encrypted blobs are unqueryable by PostGIS and would break the server-side geofencing and ML clustering planned for later slices. This is a deliberate, documented trade-off accepted by the user. Non-spatial sensitive fields may be app-encrypted if a future requirement demands it.
- **Secrets:** passwords argon2-hashed; device tokens stored only as SHA-256 hashes; JWT signing key, DB URL, and FCM service-account credentials supplied via environment variables (never committed).
- **Tenancy isolation:** all reads/writes scoped to the authenticated parent or device; ownership checked on every request.

---

## 8. Device status & alerts

This slice implements two alert types:

- **`low_battery`** — evaluated when a location batch or status heartbeat reports `battery_level` below a configurable threshold (default 15%) and `is_charging = false`. **Debounced**: at most one low-battery alert per device per cooldown window (default 60 min).
- **`offline`** — a **Vercel Cron** job runs every few minutes, finds non-revoked devices whose `last_seen_at` is older than a configurable threshold (default 30 min) with no existing open offline alert, and fires one.

Each fired alert is **persisted** to `alerts` (so `GET /alerts` returns history) **and pushed** via FCM to the parent's registered `fcm_token`; `delivered_at` is set on successful send. Current-location delivery is **poll-based** this slice (parent app polls `/location/current`); realtime push of live location is a later optimization.

Geofence enter/leave/dwell and routine-deviation alerts are **out of scope** (later slices).

---

## 9. API documentation (Swagger / OpenAPI)

- Each route's Zod schemas (request + response) are registered with `@asteasolutions/zod-to-openapi`.
- A build/route step assembles a single **OpenAPI 3.1** document covering every endpoint, both auth schemes (parent bearer JWT, device bearer token), and all error responses.
- **Swagger UI** is served at `/api/docs`; the raw spec at `/api/openapi.json`.
- Because docs are generated from the same Zod schemas used for runtime validation, documentation and validation cannot drift.

---

## 10. Scale & performance

Design assumption: thousands of devices each posting every 1–5 minutes → writes dominate. Targets and mechanisms:

- **Hot write path (`locations`):** monthly **range partitioning** on `recorded_at`; batch inserts (one statement per upload); minimal indexing on the write path beyond the required composite + spatial indexes; old partitions are cheap to archive/drop.
- **Current-location reads:** O(1) via denormalized `devices.last_location` — never scans `locations`.
- **History/alerts reads:** **cursor pagination** (keyset on `recorded_at`/`id`), never OFFSET; bounded page size.
- **Connections:** Neon serverless driver + pooled connections suited to Fluid Compute's instance reuse.
- **Rate limiting:** on `register`, `login`, `pair`, and `pairing-code` generation to resist abuse/brute force.
- **Idempotency:** unique `(device_id, recorded_at)` lets devices safely retry uploads after connectivity loss without duplicating rows.
- **Statelessness:** all endpoints stateless → horizontal scale on Vercel with no sticky sessions.

---

## 11. Error handling

- Consistent JSON error envelope: `{ error: { code, message, details? } }`.
- Zod validation failure → `400` with field-level `details`.
- Auth failure → `401`; ownership/authorization failure → `403`; missing resource → `404`; rate limit → `429`.
- Idempotent upserts on location batches (no 500 on duplicate retry).
- All unexpected errors logged server-side with a request id; never leak internals to clients.

---

## 12. Testing strategy

All tests run in **this environment** (the reason this slice was sequenced first).

- **Unit (Vitest):** password hashing/verification, device-token hashing, pairing-code generation + TTL/consume logic, JWT issue/verify, low-battery debounce rule, offline-detection predicate, cursor encode/decode, Zod schemas.
- **Integration (Vitest + real test Postgres):** every endpoint, each exercised for happy path, auth-missing/invalid (`401`), cross-tenant access (`403`), validation failure (`400`), and idempotent retry where applicable. Each test runs in a transaction rolled back on teardown for isolation.
- **Alert tests:** low-battery fires + is debounced; offline cron fires once and not again while open; both persist an `alerts` row and attempt an FCM send (FCM mocked).
- **Coverage goal:** every route and every alert rule exercised; CI fails on regressions.

---

## 13. Configuration (environment variables)

`DATABASE_URL` (Neon) · `JWT_SECRET` · `JWT_REFRESH_SECRET` · `FCM_SERVICE_ACCOUNT_JSON` (or path) · `LOW_BATTERY_THRESHOLD` (default 15) · `LOW_BATTERY_COOLDOWN_MIN` (default 60) · `OFFLINE_THRESHOLD_MIN` (default 30) · `PAIRING_CODE_TTL_MIN` (default 10) · `MAX_LOCATION_BATCH` (default 200). No secrets committed; `.env.example` documents all keys.

---

## 14. Out of scope (future slices)

Geofence enter/leave/dwell firing · ML pattern recognition (clustering, place detection, auto-labels, recurring routes) · routine-deviation alerts · unknown-location and left-school-unexpectedly alerts · native Android child app · native Android parent app + MapLibre UI · realtime live-location streaming · app-level E2EE.

---

## 15. Success criteria for this slice

- A deployed Next.js service with all §6 endpoints live and documented at `/api/docs`.
- A child device can pair, upload batched locations, and heartbeat status.
- A parent can register, create a child, generate a pairing code, and read current location, history (by date), device status, and alerts.
- Low-battery and offline alerts fire, persist, and push via FCM.
- Full unit + integration test suite green, covering every endpoint and alert rule.
- Performance mechanisms in place: partitioned `locations`, denormalized current location, cursor pagination, rate limiting, idempotent uploads.
