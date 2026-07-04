# Task 8 Report: FCM sender abstraction + low-battery alert engine

## Summary

Implemented `src/lib/alerts/fcm.ts` (PushSender abstraction, lazy firebase-admin impl,
test injection via `setSender`/`resetSender`) and `src/lib/alerts/engine.ts`
(`fireLowBatteryIfNeeded`), transcribed verbatim from the brief. Added the required
`parents.fcmToken` schema column and generated/renamed the resulting migration to
`drizzle/0002_*` to avoid colliding with the hand-written `0001_locations_partition.sql`.
Discovered and fixed a gap in the verbatim test: `seedParent()` did not set `fcmToken`,
so the alert engine's `parentFcmFor` lookup returned `null` and `getSender().send` was
never invoked, leaving `sent === 0` against the test's expectation of `1`. Fixed by
giving `seedParent` a default `fcmToken` value — a minimal, non-destructive change to
shared test infrastructure, not to engine/fcm logic.

## Files changed

- `src/lib/alerts/fcm.ts` (new) — verbatim from brief.
- `src/lib/alerts/engine.ts` (new) — verbatim from brief.
- `src/db/schema.ts` (modified) — added `fcmToken: text('fcm_token')` to `parents` table (nullable).
- `drizzle/0002_loose_weapon_omega.sql` (new) — generated migration.
- `drizzle/meta/0002_snapshot.json` (new) — generated snapshot.
- `drizzle/meta/_journal.json` (modified) — new entry for the 0002 migration.
- `test/lib/alerts.test.ts` (new) — verbatim from brief.
- `test/helpers/factories.ts` (modified) — `seedParent` now seeds a default `fcmToken`
  (`fcm-${Date.now()}`) so the alert-delivery path through `parentFcmFor` is exercised.

No package.json/package-lock.json changes were needed — `firebase-admin` was already a
dependency from project scaffolding.

## Migration generation

`npm run db:generate` initially produced `drizzle/0001_loose_weapon_omega.sql` (drizzle-kit's
journal only tracks its own generated baselines — the hand-written `0001_locations_partition.sql`
from Task 2 was never registered in `_journal.json`, confirmed via `git show e89cabc -- drizzle/meta/_journal.json`,
which shows only `0000_superb_clint_barton` at the time that hand-written file was added).
To avoid a filename collision with the existing `drizzle/0001_locations_partition.sql` (the
test harness `resetDb()` applies `drizzle/*.sql` sorted by filename), I renamed the generated
artifacts:

- `drizzle/0001_loose_weapon_omega.sql` -> `drizzle/0002_loose_weapon_omega.sql`
- `drizzle/meta/0001_snapshot.json` -> `drizzle/meta/0002_snapshot.json`
- Updated the `tag` field in `drizzle/meta/_journal.json`'s new entry to `0002_loose_weapon_omega`.

### Generated SQL (drizzle/0002_loose_weapon_omega.sql), full and unmodified:

```sql
ALTER TABLE "parents" ADD COLUMN "fcm_token" text;
```

This is the only statement in the file — no drops, no other table changes. Confirmed
non-destructive; no escalation needed.

### Final `drizzle/meta/_journal.json`:

```json
{
  "version": "7",
  "dialect": "postgresql",
  "entries": [
    {
      "idx": 0,
      "version": "7",
      "when": 1781888737028,
      "tag": "0000_superb_clint_barton",
      "breakpoints": true
    },
    {
      "idx": 1,
      "version": "7",
      "when": 1781894204107,
      "tag": "0002_loose_weapon_omega",
      "breakpoints": true
    }
  ]
}
```

## TDD evidence

### RED (Step 2 — before implementation, only the test file existed)

```
 RUN  v2.1.9 C:/git/FamilyShield

 ❯ test/lib/alerts.test.ts (0 test)

⎯⎯⎯⎯⎯⎯ Failed Suites 1 ⎯⎯⎯⎯⎯⎯⎯

 FAIL  test/lib/alerts.test.ts [ test/lib/alerts.test.ts ]
Error: Cannot find module '@/lib/alerts/engine' imported from 'C:/git/FamilyShield/test/lib/alerts.test.ts'.
...
 Test Files  1 failed (1)
      Tests  no tests
```

### Intermediate failure (after implementing fcm.ts/engine.ts/schema, before the factory fix)

This confirmed the `seedParent`/`fcmToken` gap rather than a code defect in engine.ts:

```
 ❯ test/lib/alerts.test.ts (1 test | 1 failed)
   × low-battery alert > fires once below threshold then debounces
     → expected +0 to be 1 // Object.is equality

AssertionError: expected +0 to be 1
- Expected: 1
+ Received: 0
  at test/lib/alerts.test.ts:25:18  (expect(sent).toBe(1))
```

(The `rows.toHaveLength(1)` assertion on the prior line already passed — the alert row
insert and debounce logic were correct; only the FCM send was never reached because
`parentFcmFor` returned `null`.)

### GREEN (Step 4 — after factory fix)

```
 RUN  v2.1.9 C:/git/FamilyShield

 ✓ test/lib/alerts.test.ts (1 test) 1242ms

 Test Files  1 passed (1)
      Tests  1 passed (1)
```

### Full suite regression check

```
npx vitest run
...
 ✓ test/api/children.test.ts (2 tests) 887ms
 ✓ test/api/pair.test.ts (2 tests) 999ms
 ✓ test/api/auth.test.ts (3 tests) 1405ms
 ✓ test/lib/alerts.test.ts (1 test) 1042ms
 ✓ test/lib/guards.test.ts (3 tests) 887ms
 ✓ test/lib/http.test.ts (4 tests) 34ms
 ✓ test/db.test.ts (3 tests) 932ms
 ✓ test/lib/auth.test.ts (3 tests) 261ms
 ✓ test/schema.test.ts (1 test) 5ms
 ✓ test/smoke.test.ts (1 test) 3ms

 Test Files  10 passed (10)
      Tests  23 passed (23)
```

`npx tsc --noEmit` — clean, no output, no type errors.

## Notes / deviations from brief

1. **Migration filename**: brief suggested `drizzle/0002_*.sql`; drizzle-kit's autogenerated
   name was `0001_loose_weapon_omega` (collision with the hand-written `0001_locations_partition.sql`).
   Renamed file + snapshot to `0002_*` and adjusted the journal tag, per task instructions
   ("e.g. `drizzle/0002_*.sql`"). No other content was altered.
2. **`test/helpers/factories.ts`**: not listed in the brief's "Files" section, but required
   a one-line fix (`seedParent` now sets a default `fcmToken`) for the verbatim test to pass
   as written — without it, `parentFcmFor` always returns `null` for any parent seeded via
   the existing factory, and the FCM send path (and thus `sent === 1`) is never reached.
   This is a test-infrastructure fix, not a change to engine/fcm logic, and does not affect
   any other passing test (full suite confirmed green).
3. No destructive diff in the generated migration — escalation not needed.

## Commit

`7c6d853` — `feat(familyshield): FCM sender abstraction + low-battery alert engine`

Files committed (explicit paths, no `git add .`):
`src/lib/alerts/fcm.ts`, `src/lib/alerts/engine.ts`, `src/db/schema.ts`,
`drizzle/0002_loose_weapon_omega.sql`, `drizzle/meta/0002_snapshot.json`,
`drizzle/meta/_journal.json`, `test/lib/alerts.test.ts`, `test/helpers/factories.ts`.

No package.json/package-lock.json changes (firebase-admin already present).
`.env` not touched/committed. `docker-compose.yml` (pre-existing untracked file,
unrelated to this task) left untracked.
