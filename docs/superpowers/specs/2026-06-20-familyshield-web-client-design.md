# FamilyShield Web Client — Parent Dashboard + Kid Simulator

> Design spec. Slice: browser client for the existing backend API (`src/app/api/*`).
> Constraint: white/blue palette, MapLibre GL JS + OpenStreetMap raster tiles (never Google Maps).

## Goal

Give the FamilyShield backend a browser-driveable front end so its features can be
seen working end-to-end: a **Parent dashboard** (auth, children, live location map,
status, alerts, pairing code) and a **Kid-device simulator** (pair, send location +
battery/status). Both are validated with Playwright E2E tests.

## Architecture

Add client pages to the **same** Next.js 16 app — reuses the running server and the
existing API with no CORS. New client code is isolated from the API under a route group:

```
src/app/
  layout.tsx            # root layout (required once a page route exists) + globals.css
  page.tsx              # landing: links to /parent and /kid
  globals.css           # white/blue palette, base styles
  (client)/
    parent/page.tsx     # parent dashboard (client component)
    kid/page.tsx        # device simulator (client component)
  api/...               # unchanged
src/components/client/
  MapView.tsx           # MapLibre wrapper, OSM raster tiles, controlled marker
src/lib/client/
  api.ts                # typed fetch wrappers + localStorage token helpers
```

One new dependency: `maplibre-gl`.

## Components

- **`lib/client/api.ts`** — `registerParent`, `loginParent`, `listChildren`,
  `createChild`, `currentLocation`, `listAlerts`, `pairingCode`, `pairDevice`,
  `sendLocation`, `sendStatus`. Token storage in `localStorage`
  (`fs_parent_token`, `fs_device_token`). Non-2xx → thrown error with API message;
  401 clears the stored token.
- **`MapView.tsx`** — renders a MapLibre map with the OSM raster style; props:
  `center`, `marker?`, `draggable?`, `onMarkerMove?`. Used read-only by the parent
  and draggable by the kid sim.
- **Parent `page.tsx`** — `LoginForm` (register/login toggle) → on token, `ChildList`
  (`GET /api/children`, create child, generate pairing code) → `ChildDetail`
  (`currentLocation` plotted on `MapView`, battery/online status, `alerts` list,
  Refresh button).
- **Kid `page.tsx`** — `PairForm` (code + platform → `POST /api/pair`) → `DeviceSim`
  (draggable `MapView` marker for lat/lng, battery slider, charging toggle,
  "Send location" → `POST /api/locations`, "Send status" → `POST /api/device/status`).

## Data flow (the loop Playwright asserts)

1. Parent registers/logs in, creates a child, generates a 6-digit pairing code.
2. Kid sim pairs with that code → receives + stores a device token.
3. Kid sim sends a location and a low battery (≤ threshold, not charging).
4. Parent clicks Refresh → current location marker appears, low-battery alert shows.

## Error handling

Inline messages for: bad credentials, duplicate email, invalid/expired/used pairing
code, validation failures, unauthenticated (401 clears token and returns to login).

## Testing

- **Playwright E2E** (`test/e2e/client.spec.ts`, separate Playwright config so it does
  not collide with Vitest): two browser contexts — parent and kid — drive the full
  loop above; assert the marker and the low-battery alert appear; capture screenshots
  at each milestone.
- **Backend**: existing 38 Vitest tests remain green; `tsc --noEmit` and `next build`
  stay clean.

## Out of scope (this slice)

Safe-zone drawing, history timeline scrubbing, auto-ticking movement, real native
apps, production auth hardening of the browser token storage.
