# FamilyShield

FamilyShield is a native Android parental-safety app backed by a Next.js API and
PostgreSQL/PostGIS. The Android app in `android/` is the product client; the
Next.js application exposes the API, health endpoint, and Swagger documentation.

For the feature inventory and screen-level overview, see [FEATURES.md](FEATURES.md).

## Clients

- **Android app** (`android/`): Kotlin + Jetpack Compose app with Parent and Kid
  device experiences. A paired kid install opens directly into kid mode until all
  parents are unpaired from the device.
- **Backend API** (`src/app/api/`): parent auth, pairing, child/device data,
  location, status, app usage, safe zones, alerts, messages, cron jobs, and OpenAPI.

## Stack

Next.js 16 App Router, TypeScript, Drizzle ORM, PostgreSQL + PostGIS, Zod, `jose`,
`argon2`, `firebase-admin`, Swagger/OpenAPI, Vitest, Kotlin, Jetpack
Compose, Material 3, OkHttp, kotlinx.serialization, and osmdroid/OpenStreetMap.

## Prerequisites

- Node.js 20+ and npm
- Docker Desktop
- Android Studio or Android SDK
- JDK 17+

## Setup

```bash
npm install
cp .env.example .env
docker compose up -d db
npm run db:setup
```

The local `.env` should point at the Docker PostGIS database on port `5433`.
The example chat-encryption key is for local development only. Production must
use a separately generated, backed-up 32-byte base64 key.
`npm run db:setup` resets and rebuilds the local development schema, including all
SQL migrations under `drizzle/`.

## Running

```bash
npm run dev        # http://localhost:3000
npm run verify     # TypeScript + Vitest
npm run build      # production build
npm run test:auth-emulator
```

Useful local routes:

- `http://localhost:3000/api/docs`
- `http://localhost:3000/api/openapi.json`
- `http://localhost:3000/api/health`

### Chat security and retention

Chat bodies are encrypted in the database with AES-256-GCM using
`MESSAGE_ENCRYPTION_KEY`. Encryption is application-level encryption at rest,
not end-to-end encryption: the backend decrypts authorized API responses and
includes a truncated preview in chat push notifications. Message retention
defaults to 365 days and is configurable with `MESSAGE_RETENTION_DAYS`.

## Android

```bash
cd android
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.familyshield.app -c android.intent.category.LAUNCHER 1
```

Debug builds default to `http://10.0.2.2:3000`, the Android emulator alias for the
host machine's `localhost`. For a physical device, build with a reachable HTTPS or
LAN backend:

```bash
./gradlew :app:assembleDebug -PAPI_BASE=http://192.168.x.x:3000
```

## API Authentication

| Audience | Mechanism | Header |
|---|---|---|
| Parent | Firebase email/password or Google sign-in; verified ID token plus App Check | `Authorization: Bearer <firebaseIdToken>` and `X-Firebase-AppCheck` |
| Kid device | Opaque token issued by `/api/pair`, stored server-side as a SHA-256 hash | `Authorization: Bearer <deviceToken>` |

Parent routes authorize by linked child ownership. Device routes authorize by the
paired device token.

## API Surface

Device-facing routes:

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/pair` | Exchange a pairing code for a device token |
| POST | `/api/locations` | Upload location points |
| POST | `/api/device/status` | Upload battery, charging, and FCM status |
| POST | `/api/device/telemetry` | Upload combined location/status/app-usage telemetry |
| POST | `/api/device/app-usage` | Upload app screen-time aggregates |
| GET | `/api/device/monitoring` | List parents monitoring this kid device |
| DELETE | `/api/device/monitors/{parentId}` | Unpair one monitoring parent |
| GET / POST | `/api/device/monitors/{parentId}/messages` | Kid chat with one parent |

Parent-facing routes:

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/auth/bootstrap` | Map a verified Firebase identity to its Neon parent |
| POST | `/api/auth/legacy-migrate` | One-time, rate-limited password migration |
| POST | `/api/auth/revoke-sessions` | Revoke all Firebase refresh tokens after recent authentication |
| GET / POST | `/api/children` | List/create children |
| GET | `/api/children/{id}` | Child detail |
| POST | `/api/children/{id}/pairing-code` | Generate a pairing code |
| GET | `/api/children/{id}/location/current` | Latest location |
| GET | `/api/children/{id}/location/history` | Cursor-paginated location history |
| GET | `/api/children/{id}/routes` | Route and stop detection from location history |
| GET | `/api/children/{id}/alerts` | Cursor-paginated alerts |
| GET / POST | `/api/children/{id}/messages` | Parent chat thread |
| GET / POST | `/api/children/{id}/zones` | Safe-zone list/create |
| DELETE | `/api/children/{id}/zones/{zoneId}` | Delete a safe zone |
| GET | `/api/children/{id}/app-usage` | Screen-time summary |
| GET | `/api/messages/summary` | Parent conversation summary |
| DELETE | `/api/devices/{id}` | Revoke a paired device |
| POST | `/api/parent/push-token` | Register a parent FCM token |

Internal cron routes:

- `GET /api/cron/offline-sweep`
- `GET /api/cron/location-retention`
- `GET /api/cron/message-retention`

All cron routes require `Authorization: Bearer $CRON_SECRET`.

## Database

`drizzle/0001_locations_partition.sql` replaces the generated `locations` table
with a monthly partitioned PostGIS table. Production setup should use:

```bash
npm run db:prod:setup
npm run db:check
```

Before migrating parent authentication, run `npm run auth:preflight`. Apply
`drizzle/0019_firebase_parent_auth.sql`, then run `npm run auth:provision` and
review its dry-run output before `npm run auth:provision:apply`.

For an existing deployment, set `MESSAGE_ENCRYPTION_KEY` first and then run
`npm run db:encrypt-messages` once to encrypt legacy plaintext chat rows.

When app-usage metadata is added to an existing database, apply
`drizzle/0011_app_usage_metadata.sql`.

## Demo Data

```bash
node scripts/seed-test-account.mjs
node scripts/seed-demo.mjs
node scripts/seed-app-usage.mjs
```

`seed-test-account.mjs` creates a small single-child account. `seed-demo.mjs`
creates the richer multi-child demo family. `seed-app-usage.mjs` adds screen-time
data for all children in the target database.

## Google Play Policy Posture

The Android app declares the `isMonitoringTool` metadata flag with
`child_monitoring`, does not request `QUERY_ALL_PACKAGES`, and uses scoped package
visibility queries for launcher/home apps. Kid monitoring uses a visible foreground
service, runtime permissions, `UsageStatsManager` access only when the user grants
usage access, and background location only for the paired kid-device flow.

Before production release, complete Play Console declarations for child monitoring,
background location, foreground service type, data safety, privacy policy, and any
regional child-safety obligations.

## Project Structure

```text
android/                     Native Android app
src/app/api/                 Next.js REST API routes
src/db/                      Drizzle schema and database client
src/lib/                     Auth, validation, telemetry, alerts, routes, OpenAPI
drizzle/                     SQL migrations
scripts/                     Database setup and demo seed scripts
test/                        Vitest API and library integration tests
docs/                        Deployment notes, Google sign-in notes, design archive
```

## Production Notes

- Configure one Firebase project for Authentication, App Check, and Cloud
  Messaging. Enable Email/Password and Google, one account per email, email
  enumeration protection, the selected password policy, and Play Integrity.
- Keep legacy JWT secrets only through the configured 30-day transition cutoff.
- Restore scheduled Vercel cron jobs before launch if `vercel.json` has an empty
  `crons` array during free-tier development.
- Replace the in-memory rate limiter with a shared store such as Redis/Upstash
  before horizontal backend scale-out.
