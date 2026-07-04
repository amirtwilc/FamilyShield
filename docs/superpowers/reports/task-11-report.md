# Task 11 Report: Current location, history (cursor-paginated), and parent push-token

## Status: DONE

## Summary

Implemented Task 11 exactly per the brief (`C:\git\.git\sdd\task-11-brief.md`), transcribing the provided code verbatim:

- `src/lib/schemas/parent.ts` — `pushTokenSchema` (validates `{ fcm_token: string }`) and `historyQuery` (validates `date` as `YYYY-MM-DD`, optional `cursor`, `limit` coerced int 1-500 default 200).
- `src/app/api/children/[id]/location/current/route.ts` — `GET`. Requires parent auth via `requireParent`, checks `assertChildOwned` (404 if not owned), then reads the latest denormalized location off `devices` via `ST_Y(d.last_location) AS lat, ST_X(d.last_location) AS lng, d.last_location_at AS recorded_at`, ordered by `last_location_at DESC LIMIT 1`. Returns `{ lat, lng, recordedAt }` or `null` if the device has no location yet.
- `src/app/api/children/[id]/location/history/route.ts` — `GET`. Same auth/ownership guard, validates query via `historyQuery`, builds day bounds (`date T00:00:00Z` to `date T23:59:59.999Z`), decodes an optional cursor (`decodeCursor`) and applies keyset pagination via the tuple predicate `(l.recorded_at, l.id) > (${cur.recordedAt}, ${cur.id})` when a cursor is present, ordered `recorded_at ASC, id ASC`, fetching `limit + 1` rows to detect a next page. Returns `{ points: [{ lat, lng, recordedAt, speed, accuracy }], nextCursor }` where `nextCursor` is `encodeCursor(...)` of the last row on the page, or `null` if there's no more data.
- `src/app/api/parent/push-token/route.ts` — `POST`. Requires parent auth, validates body via `pushTokenSchema`, updates `parents.fcmToken` (column already existed from Task 8) via Drizzle `db.update(parents).set({ fcmToken }).where(eq(parents.id, a.parentId))`. Returns `{ ok: true }`.
- `test/api/location-read.test.ts` — transcribed verbatim from the brief. Seeds a parent/child/device, uploads two location points via the existing `POST /api/locations` upload route, then exercises `current` and `history` route handlers directly (no HTTP server, direct function calls per the project's existing test convention), and a second test verifying cross-tenant isolation (404 on a child the requesting parent doesn't own).

No deviations from the brief were needed. All consumed helpers (`requireParent`, `assertChildOwned`, `decodeCursor`/`encodeCursor`, `parseQuery`/`parseBody`, `ok`/`err`, `db`, schema tables) already existed from prior tasks and matched the expected signatures/column names (`devices.last_location`, `devices.last_location_at`, `locations.geom`, `locations.recorded_at`, `locations.id`, `parents.fcmToken`).

## Test run

```
npx vitest run test/api/location-read.test.ts
```
Result: PASS — 2/2 tests passed (975ms), including the keyset pagination tuple comparison `(l.recorded_at, l.id) > (cursor.recordedAt, cursor.id)` against the live PostGIS DB. No Postgres errors encountered with the tuple comparison — it worked on the first attempt, so the BLOCKED escalation path was not needed.

Full regression check:
```
npx vitest run
```
Result: PASS — 13 test files, 28 tests, all green. No regressions in pre-existing suites (auth, children, pair, locations, status, alerts, guards, http, db, schema, smoke).

No dedicated lint/typecheck npm script exists in this project (`package.json` only has `dev`, `build`, `start`, `test`, `test:watch`, `db:generate`, `db:migrate`); TypeScript correctness is implicitly verified by vitest's esbuild transform succeeding and all tests passing.

## Commit

Repo note: `FamilyShield` is a subdirectory inside a larger top-level git repo at `C:\git`, not its own repository. Staged and committed using paths relative to `C:\git` via explicit `git add <path>` (never `git add .` or `-A`):

```
git add "FamilyShield/src/app/api/children/[id]/location" "FamilyShield/src/app/api/parent" "FamilyShield/src/lib/schemas/parent.ts" "FamilyShield/test/api/location-read.test.ts"
```

Commit: `8a241f3` — `feat(familyshield): current location, history pagination, parent push-token`

5 files changed, 133 insertions(+):
- `FamilyShield/src/app/api/children/[id]/location/current/route.ts`
- `FamilyShield/src/app/api/children/[id]/location/history/route.ts`
- `FamilyShield/src/app/api/parent/push-token/route.ts`
- `FamilyShield/src/lib/schemas/parent.ts`
- `FamilyShield/test/api/location-read.test.ts`

No `.env` files touched or committed. An unrelated pre-existing untracked file (`FamilyShield/docker-compose.yml`, presumably from an earlier task's local setup) was deliberately left untouched/unstaged.

## Concerns / notes

- None blocking. The keyset pagination tuple comparison worked without modification against the live Postgres/PostGIS instance.
- Minor: `assertChildOwned` returns the child row (or `undefined`/`null`), not a strict boolean, but the brief's `!(await assertChildOwned(...))` truthy/falsy check works correctly with this return type — confirmed via the "forbids reading a child you do not own" test passing (404).
- Line-ending warnings ("LF will be replaced by CRLF") appeared during `git add` — cosmetic, consistent with how other files in this Windows checkout are handled; no action taken since it didn't affect previous tasks' commits.
