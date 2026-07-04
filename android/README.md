# FamilyShield — Native Android Client

The native Android client for FamilyShield. One app with a role chooser:

- **Parent dashboard** — register/login, add children, generate pairing codes, and
  watch a child's live location on an OpenStreetMap map with online/battery status
  and alerts.
- **Kid device client** — pair with a 6-digit code, then report real location and
  battery/charging status to the backend.

It talks to the [FamilyShield backend](../README.md) REST API.

## Stack

Kotlin · Jetpack Compose + **Material 3** (incl. **Material 3 Adaptive** —
`ListDetailPaneScaffold` for the foldable/large-screen parent dashboard) · Navigation
Compose · OkHttp + kotlinx.serialization · Coroutines · **osmdroid** (OpenStreetMap —
never Google Maps, per project constraints). Min SDK 26, target/compile SDK 35.

The UI follows the `android-skills` **M3 compliance audit**: adaptive List-Detail layout,
theme-token colors/shapes, accessible semantics (content descriptions, headings),
lifecycle-aware map, predictive back, and rich motion (animated panes, shimmer skeletons,
content-size/colour animations, snackbar feedback).

> UI/UX direction informed by the official [`android/skills`](https://github.com/android/skills)
> Compose guidance and the [`awesome-android-ui`](https://github.com/wasabeef/awesome-android-ui)
> pattern catalog. (Those `android/skills` are packaged for Google's `android` CLI /
> Gemini-Antigravity agents, a different ecosystem from Claude Code, so they were used
> as design reference rather than installed as agent skills.)

## Prerequisites

- Android Studio (bundles a JDK 17) or a standalone JDK 17 + Android SDK (platforms
  android-35, build-tools 35).
- The backend running and reachable. The app targets `http://10.0.2.2:3000`
  (`API_BASE_URL` in `app/build.gradle.kts`) — the Android emulator's alias for the
  host machine's `localhost`.

## Build & run

```bash
# 1. Start the backend (from the repo root)
npm run db:setup && npm run dev          # http://localhost:3000

# 2. Build the app  (JAVA_HOME must point at a JDK 17, e.g. Android Studio's jbr)
cd android
./gradlew :app:assembleDebug             # -> app/build/outputs/apk/debug/app-debug.apk

# 3. Install & launch on an emulator/device
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.familyshield.app -c android.intent.category.LAUNCHER 1
```

For a physical phone using the Vercel backend, build with the deployed HTTPS API:

```bash
./gradlew :app:assembleDebug -PAPI_BASE=https://your-familyshield.vercel.app
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The kid mode requests location permissions and runs a visible foreground monitoring
service that uploads location and battery/status about every 5 minutes while paired.

`local.properties` (git-ignored) must point `sdk.dir` at your Android SDK.

## Structure

```
app/src/main/java/com/familyshield/app/
  MainActivity.kt          # NavHost + role chooser (home)
  net/                     # ApiClient, Models, TokenStore
  parent/                  # ParentViewModel + ParentApp (login → dashboard → child detail)
  kid/                     # KidViewModel + KidApp (pair → monitored device)
  ui/                      # OsmMap (osmdroid in Compose), TapOverlay, theme/
```

## Verified flow

Parent registers → adds a child → generates a pairing code; the kid device pairs
with that code and sends battery + location; the parent refreshes and sees the
device online, the location on the map, and a low-battery alert. Verified on a
Pixel Fold (API 35) emulator against the running backend.
