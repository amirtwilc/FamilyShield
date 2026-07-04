# FamilyShield — Native Android Client

> Design spec. Slice: native Android client for the existing backend API.
> Constraint: white/blue palette, OpenStreetMap (osmdroid) — never Google Maps.

## Goal

Provide the FamilyShield **client side as a native Android app** (Kotlin + Jetpack
Compose), replacing the web client as the primary client surface. One app, two roles:
a Parent dashboard and a Kid-device simulator, both driving the existing REST API.

## Architecture

Single-module Android app (`android/app`) under the repo. Compose UI with
Navigation Compose; a `NavHost` with three destinations: `home` (role chooser),
`parent`, `kid`. Each role is a self-contained feature package with its own
`AndroidViewModel` holding UI state and calling a shared `ApiClient`.

```
app/src/main/java/com/familyshield/app/
  MainActivity.kt          # NavHost + role chooser
  net/ApiClient.kt         # OkHttp + kotlinx.serialization wrapper
  net/Models.kt            # @Serializable request/response DTOs
  net/TokenStore.kt        # SharedPreferences: parent JWT + device token
  parent/ParentViewModel.kt, ParentApp.kt
  kid/KidViewModel.kt, KidApp.kt
  ui/OsmMap.kt, TapOverlay.kt, theme/Theme.kt
```

## Networking

`ApiClient` exposes suspend functions mirroring the API (register, login,
listChildren, createChild, pairingCode, currentLocation, alerts, pair, sendLocation,
sendStatus). Base URL `http://10.0.2.2:3000` (emulator → host). Cleartext allowed
only for `10.0.2.2`/`localhost` via a network-security-config. Errors surface as
`ApiException(status, message)` parsed from the API's `{ error: { message } }`.

## UI / UX

Material 3, white surfaces + blue (`#1E6EF0`) primary, matching the project palette.
ElevatedCards, status pills (online/battery), a Slider for battery, and an osmdroid
`MapView` wrapped for Compose (read-only marker for the parent; tap-to-move for the
kid). Direction informed by `android/skills` (Compose) and `awesome-android-ui`.

## Data flow (verified loop)

Parent registers → creates a child → generates a pairing code → kid simulator pairs
and sends a low battery + location → parent refreshes → device shows online, location
on the map, and a low-battery alert. Verified on a Pixel Fold (API 35) emulator.

## Out of scope (this slice)

Real GPS/background location, FCM push receipt UI, safe-zone drawing, history
timeline, two separate parent/kid APKs (single app with a role chooser is used),
release signing / Play distribution.
