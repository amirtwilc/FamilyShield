# FamilyShield Android App

The native Android client for FamilyShield. It is one app with two experiences:

- **Parent dashboard**: sign in, manage children, generate pairing codes, view
  location/status/alerts/app usage, manage zones, and chat.
- **Kid device**: pair with a six-digit code, then report location, battery/status,
  app usage, and kid messages to the backend.

A paired kid device owns that install until all monitoring parents are unpaired.
Reopening the app goes straight back to the kid device screen. Parent login is
available only when the device is not paired as a kid device.

## Stack

Kotlin, Jetpack Compose, Material 3, Navigation Compose, OkHttp,
kotlinx.serialization, coroutines, and osmdroid/OpenStreetMap. Min SDK 26,
target/compile SDK 35.

## Backend

Debug emulator builds default to:

```text
http://10.0.2.2:3000
```

That is the Android emulator alias for the host machine's `localhost`. For a
physical device, build with a reachable backend:

```bash
./gradlew :app:assembleDebug -PAPI_BASE=http://192.168.x.x:3000
```

Use HTTPS for deployed builds.

## Build And Run

From the repo root, start the backend first:

```bash
npm run db:setup
npm run dev
```

Then build and install the Android app:

```bash
cd android
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.familyshield.app -c android.intent.category.LAUNCHER 1
```

`local.properties` is git-ignored and must point `sdk.dir` at the local Android SDK.

## Monitoring Permissions

Kid mode requests sensitive permissions only after pairing:

- foreground/background location for child safety location reporting
- usage access via Android settings for screen-time telemetry
- notification permission for visible foreground monitoring notifications

The manifest includes the Google Play `isMonitoringTool` metadata flag with
`child_monitoring`, a visible location foreground service, scoped package visibility
queries, and no `QUERY_ALL_PACKAGES` permission.

## Structure

```text
app/src/main/java/com/familyshield/app/
  MainActivity.kt          NavHost and parent/kid startup routing
  net/                     ApiClient, models, token storage
  parent/                  Parent UI, ViewModel, app-usage screen
  kid/                     Kid UI, ViewModel, telemetry, monitoring service
  ui/                      Maps, shared Compose UI, theme
```

## Verification

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```
