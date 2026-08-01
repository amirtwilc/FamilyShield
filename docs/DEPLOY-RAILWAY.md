# Deploying FamilyShield to Railway

The backend is a Next.js (Node) service that needs **PostgreSQL with PostGIS**.
Railway's default Postgres plugin does **not** include PostGIS, so the database
must run the `postgis/postgis` image.

## 1. Create the project
Railway → **New Project** → **Deploy from GitHub repo** → pick
`BugHunter82/FamilyShield` (authorize Railway's GitHub app for the private repo).
Railway detects `Dockerfile` + `railway.json` and builds the web service.

## 2. Add the database (PostGIS)
In the project → **New** → **Database** is vanilla Postgres (no PostGIS). Instead:
**New** → **Empty Service** → **Source: Docker Image** → `postgis/postgis:16-3.4`.
Set service variables:
```
POSTGRES_USER=familyshield
POSTGRES_PASSWORD=<a-strong-password>
POSTGRES_DB=familyshield
```
Add a **Volume** mounted at `/var/lib/postgresql/data` so data persists.

## 3. Configure the web service variables
On the FamilyShield (web) service → **Variables**:
```
DATABASE_URL=postgresql://familyshield:<password>@<db-service>.railway.internal:5432/familyshield
TRUST_PROXY=true
JWT_SECRET=<32-byte random>
JWT_REFRESH_SECRET=<32-byte random>
CRON_SECRET=<32-byte random>
MESSAGE_ENCRYPTION_KEY=<base64-encoded-32-byte-key>
MESSAGE_RETENTION_DAYS=365
LOW_BATTERY_THRESHOLD=15
LOW_BATTERY_COOLDOWN_MIN=60
OFFLINE_THRESHOLD_MIN=30
PAIRING_CODE_TTL_MIN=10
MAX_LOCATION_BATCH=200
LOCATION_MAX_FUTURE_MIN=10
LOCATION_MAX_BACKFILL_DAYS=90
PG_POOL_MAX=10
# Optional push notifications:
FCM_SERVICE_ACCOUNT_JSON=
```
Use Railway's **internal** DB hostname (`*.railway.internal`) for `DATABASE_URL`.
Generate secrets with: `node -e "console.log(require('crypto').randomBytes(32).toString('base64url'))"`.
Generate `MESSAGE_ENCRYPTION_KEY` with:
`node -e "console.log(require('crypto').randomBytes(32).toString('base64'))"`.
Back this key up securely; losing it makes encrypted chat history unreadable.

The container's start command runs `scripts/db-init.mjs` (creates the PostGIS
extension + applies every `drizzle/*.sql`) before `next start`, so the schema is
provisioned on first boot. Health check: `GET /api/health`.

## 4. Public domain
Web service → **Settings → Networking → Generate Domain** → you get
`https://<name>.up.railway.app` (TLS included). Add a custom domain later if you want.

For an existing database, run `npm run db:encrypt-messages` once after the
encryption key is configured.

## 5. Schedule the maintenance crons
Project → **New** → **Cron** (or a separate service) hitting:
```
GET https://<your-domain>/api/cron/offline-sweep
Header: Authorization: Bearer <CRON_SECRET>
```
e.g. every 5 minutes (`*/5 * * * *`).

Also schedule `/api/cron/location-retention` hourly and
`/api/cron/message-retention` daily with the same authorization header.

## 6. Point the Android release app at the deployed server
```
cd android
./gradlew :app:assembleRelease -PAPI_BASE=https://<your-domain>
```
Release builds are HTTPS-only (no cleartext), so the deployed URL must be `https://`.
(Debug builds keep the LAN/emulator HTTP default.)

## 7. Verify
```
curl https://<your-domain>/api/health        # {"ok":true}
curl https://<your-domain>/api/openapi.json   # OpenAPI document
```
Then seed a test account against the live URL:
```
SEED_BASE_URL=https://<your-domain> node scripts/seed-demo.mjs
```

## CLI alternative (Account token)
With a Railway **Account** token you can drive it from a terminal:
```
export RAILWAY_API_TOKEN=<account-token>   # railway.app → Account → Tokens
railway link                               # select the project
railway up                                 # build + deploy the current repo
railway variables --set JWT_SECRET=... --set ...
```
The PostGIS database service is still easiest to add from the dashboard (step 2).
