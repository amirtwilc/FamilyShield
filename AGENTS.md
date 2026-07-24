# Agent Instructions

Read this file before working on FamilyShield.

FamilyShield is primarily a native Android parental-safety app backed by a Next.js API and a PostGIS database. The web client in this repo is not the main app; it is a testing/development client for exercising the backend.

## What The Project Does

FamilyShield lets a parent monitor and communicate with a child's paired device.

Core flow:

1. A parent creates an account or signs in.
2. The parent creates a child profile.
3. The parent generates a 6-digit pairing code.
4. A kid device pairs with that code.
5. The kid device sends location, battery/status, app-usage, and messages.
6. The parent can view location, alerts, safe zones, chat, history, routes, and app usage.

## Main Product

The main app is the native Android app in `android/`.

It is a Kotlin + Jetpack Compose app with two modes:

- Parent dashboard
- Kid device simulator/client

The Android app talks to the backend REST API.

## Backend

The backend lives in the Next.js app under `src/app/api/`.

It provides:

- Parent auth: register, login, refresh, optional Google sign-in
- Child profile management
- Device pairing
- Location ingestion and current/history reads
- Device status and battery alerts
- Offline sweep cron
- Safe-zone storage
- Parent/kid messages
- App-usage reporting
- OpenAPI/Swagger docs

The database layer uses Drizzle ORM with PostgreSQL + PostGIS. Schema definitions are in `src/db/schema.ts`, and SQL migrations are in `drizzle/`.

## Web Client

The web client is not the main application.

It is bundled into the Next.js app for testing, development, and quick manual API exercising.

Useful routes:

- `/parent` - browser parent dashboard/test client
- `/kid` - browser kid-device simulator/test client
- `/api/docs` - Swagger UI
- `/api/openapi.json` - OpenAPI JSON

Do not treat the web client as the primary product UX. The Android app is the primary client.

## Change Reporting

After making code changes, explicitly summarize which deployable components changed so the user knows what must be pushed, migrated, redeployed, rebuilt, or reinstalled.

Always include a short component impact list using these labels when applicable:

- `DB`: schema changes, Drizzle schema changes, SQL migrations, seed/setup scripts, retention/storage behavior that requires a migration or database command.
- `Backend`: Next.js API routes, server libraries, auth, alerts, cron jobs, OpenAPI, web test client, or any code that requires redeploying the backend.
- `Android app`: Kotlin/Compose/client code, Android resources, Gradle config, permissions, services, or anything that requires rebuilding and reinstalling the APK.
- `Tests/docs only`: tests, README/docs, AGENTS.md, or developer-only files that do not require runtime deployment.

If `DB` changed, name the exact migration file or database command to run. If `Backend` changed, say that the backend must be redeployed. If `Android app` changed, say that a new APK must be built and installed.

## Testing Requirements

Always add or update tests for new features and behavioral fixes when the behavior can be covered by the repo's test tooling. Keep tests focused on the changed behavior.

After every code change, run the relevant automated tests before reporting completion. At minimum, run backend type-check/tests for backend changes and Android unit/build verification for Android changes.

## Local Development Requirements

Required tools:

- Node.js 20+
- npm
- Docker Desktop
- Android Studio or Android SDK
- JDK 17+; Android Studio's bundled JBR works well

## Local Environment

For local development, `.env` should point at the Docker PostGIS database:

```env
DATABASE_URL=postgres://familyshield:familyshield@localhost:5433/familyshield
TEST_DATABASE_URL=postgres://familyshield:familyshield@localhost:5433/familyshield_test
JWT_SECRET=change-me-access
JWT_REFRESH_SECRET=change-me-refresh
FCM_SERVICE_ACCOUNT_JSON=
LOW_BATTERY_THRESHOLD=15
LOW_BATTERY_COOLDOWN_MIN=60
OFFLINE_THRESHOLD_MIN=30
PAIRING_CODE_TTL_MIN=10
MAX_LOCATION_BATCH=200
CRON_SECRET=change-me-cron
```

## Run Locally

From the repo root:

```powershell
docker compose up -d db
npm install
npm run db:setup
npm run dev
```

The backend and web test client then run at:

```text
http://localhost:3000
```

Useful URLs:

```text
http://localhost:3000/parent
http://localhost:3000/kid
http://localhost:3000/api/docs
http://localhost:3000/api/health
```

For normal local development, prefer running only the DB in Docker and running Next.js directly with `npm run dev`.

Avoid `docker compose up -d --build` unless Docker Desktop has enough free disk space, because building the backend image can be brittle when Docker's data disk on `C:` is nearly full.

## Production Reminder

During development on Vercel's free tier, `vercel.json` may use `"crons": []`. Before a production launch, restore the scheduled cron jobs so offline alerts and location-retention cleanup run automatically:

```json
{
  "crons": [
    { "path": "/api/cron/offline-sweep", "schedule": "*/5 * * * *" },
    { "path": "/api/cron/location-retention", "schedule": "0 * * * *" }
  ]
}
```

Before a production Android release, configure Firebase Cloud Messaging on both sides so parent push notifications can be delivered immediately:

- Backend: set `FCM_SERVICE_ACCOUNT_JSON` to the Firebase service-account JSON for the production Firebase project.
- Android app: add the production Firebase `google-services.json` at `android/app/google-services.json` before building the production APK/AAB. Without this file, Firebase Messaging cannot issue a real FCM token for the parent device, and the backend has no target for push notifications.

## Run The Android App In An Emulator

Keep the backend running first:

```powershell
docker compose up -d db
npm run dev
```

In another terminal:

```powershell
cd android
.\gradlew.bat :app:assembleDebug
```

Start the emulator:

```powershell
emulator -list-avds
emulator '@<avd-name>'
```

Install and launch the app:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell monkey -p com.familyshield.app -c android.intent.category.LAUNCHER 1
```

The debug Android build defaults to:

```text
http://10.0.2.2:3000
```

That is the Android emulator alias for the host machine's `localhost:3000`.

## Common Local Gotchas

- `JAVA_HOME` must point to the JDK root, not the `bin` folder.
- `android/local.properties` is local-only and should contain the Android SDK path for the current machine:

```properties
sdk.dir=<absolute path to Android SDK>
```

- Account registration requires a valid email and a password of at least 8 characters.
- The local dev schema command `npm run db:setup` resets the public schema in the dev DB.
- If Docker commands appear to do nothing, Docker Desktop may be wedged. Restart Docker Desktop and check available `C:` drive space.

### Windows Example Paths

These paths are examples only and may differ on another machine:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:Path="$env:ANDROID_HOME\emulator;$env:ANDROID_HOME\platform-tools;$env:Path"
```

Example `android/local.properties` on Windows:

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

## Useful Verification Commands

Backend health:

```powershell
Invoke-RestMethod http://localhost:3000/api/health
```

Database container:

```powershell
docker compose ps
```

Android devices:

```powershell
adb devices
```

Type-check:

```powershell
npx tsc --noEmit
```

Tests:

```powershell
npm test
npm run test:e2e
```
