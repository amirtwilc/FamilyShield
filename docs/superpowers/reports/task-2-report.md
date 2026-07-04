# Task 2 Report: DB client, PostGIS migration, partitioning, test harness

## Status: DONE

## What was done

Implemented the runtime DB layer, raw-SQL partition migration, and test
harness per the brief, applying all 5 required deviations.

### Files created
- `src/db/client.ts` — node-postgres `Pool` + `drizzle-orm/node-postgres` instance (not neon-serverless).
- `src/db/partitions.ts` — `ensureLocationPartition(date)`, verbatim from brief.
- `src/lib/auth/device.ts` — `hashToken(token)` (sha256 hex) and `createDeviceToken()` only; no `requireDevice` (reserved for Task 4b).
- `drizzle/0000_superb_clint_barton.sql` + `drizzle/meta/{0000_snapshot.json,_journal.json}` — generated via `npm run db:generate` from `src/db/schema.ts` (drizzle-kit's random baseline name, not renamed).
- `drizzle/0001_locations_partition.sql` — hand-written exactly per brief: drops generated `locations`, recreates RANGE-partitioned by `recorded_at` with PK `(id, recorded_at)`, dedupe unique index, device/time btree index, GiST geom index.
- `test/helpers/db.ts` — `resetDb()` and `withRollback()`.
- `test/helpers/factories.ts` — `seedParent`, `seedChild`, `seedDevice`, verbatim from brief.
- `test/db.test.ts` — verbatim from brief.

### Files modified
- `vitest.config.ts` — added `import 'dotenv/config';` as the first line.
- `package.json` / `package-lock.json` — added `pg` (dep) and `@types/pg` (devDep).

## Deviations applied (all 5 from the task instructions)

1. **Driver swap.** `src/db/client.ts` uses `import { Pool } from 'pg'` and
   `drizzle-orm/node-postgres` instead of `@neondatabase/serverless` /
   `drizzle-orm/neon-serverless`, since Neon's driver speaks WebSocket and
   cannot reach the local Dockerized Postgres over plain TCP. Installed via
   `npm install pg` + `npm install -D @types/pg`. Left
   `@neondatabase/serverless` in `package.json` as-is (unused, not asked to
   remove) — grepped `src/` to confirm nothing else references it.

2. **Vitest dotenv loading.** Added `import 'dotenv/config';` as the first
   line of `vitest.config.ts`. Verified `TEST_DATABASE_URL` resolves inside
   tests (the DB-connecting tests in `db.test.ts` pass, which would be
   impossible if the URL were undefined and `pg.Pool` fell back to default
   connection params).

3. **`resetDb` ordering — PostGIS before tables, directory scan instead of
   hardcoded filenames.** `test/helpers/db.ts`'s `resetDb()`:
   - `DROP SCHEMA public CASCADE; CREATE SCHEMA public;`
   - `CREATE EXTENSION IF NOT EXISTS postgis;`
   - `readdirSync('drizzle').filter(f => f.endsWith('.sql')).sort()` then
     applies each file's contents via `db.execute(sql.raw(body))` in
     ascending order — `0000_superb_clint_barton.sql` before
     `0001_locations_partition.sql`, with no hardcoded `0000_init.sql`.
   This was necessary because the generated baseline creates
   `geometry(Point,4326)` columns, which fail to parse without postgis
   already present in the (freshly recreated, postgis-stripped) schema.

4. **Generated baseline migration.** Ran `npm run db:generate`, which
   produced `drizzle/0000_superb_clint_barton.sql` (drizzle-kit's random
   suffix, left as-is per instructions) plus `drizzle/meta/0000_snapshot.json`
   and `drizzle/meta/_journal.json` (drizzle-kit's tracking metadata,
   committed alongside so future `db:generate` diffs correctly). Then
   hand-wrote `drizzle/0001_locations_partition.sql` exactly per the brief.

5. **`hashToken` unblock.** Created `src/lib/auth/device.ts` with only
   `hashToken` (sha256 hex via `node:crypto`) and `createDeviceToken()`
   (32-byte base64url token + its hash) — no `requireDevice`, which is
   explicitly Task 4b's responsibility.

## RED / GREEN evidence

**RED** (`npx vitest run test/db.test.ts` before implementation):
```
FAIL  test/db.test.ts [ test/db.test.ts ]
Error: Cannot find module '@/db/client' imported from 'C:/git/FamilyShield/test/db.test.ts'.
Test Files  1 failed (1)
     Tests  no tests
```

**GREEN** (`npx vitest run test/db.test.ts` after implementation):
```
✓ test/db.test.ts (3 tests) 553ms
Test Files  1 passed (1)
     Tests  3 passed (3)
```

**Full suite** (`npx vitest run`):
```
✓ test/db.test.ts (3 tests) 595ms
✓ test/schema.test.ts (1 test) 3ms
✓ test/smoke.test.ts (1 test) 2ms
Test Files  3 passed (3)
     Tests  5 passed (5)
```

Live DB used: Docker container `familyshield-db` (`postgis/postgis:16-3.4`),
reachable on `localhost:5433`, `familyshield_test` database — confirmed
healthy via `docker ps` before running tests.

## Concerns

- **Pre-existing `tsc --noEmit` strictness nit (not introduced by this
  task, not blocking):** `npx tsc --noEmit` reports one error —
  `test/db.test.ts(26,12): error TS18048: 'p' is possibly 'undefined'.`
  This is on `expect(p.id).toBeTruthy();`, where `p` comes from
  `const p = await seedParent();` and `seedParent` destructures
  `const [p] = await db.insert(...).returning();`. With the project's
  `tsconfig.json` `noUncheckedIndexedAccess: true` (set in Task 0, not
  this task), drizzle's `.returning()` array type makes the destructured
  element `T | undefined`, and accessing `.id` on it without a null check
  flags under strict indexed access. `test/db.test.ts` is required brief
  code verbatim, and `factories.ts` is required brief code verbatim too
  (its own `p`/`c`/`d` destructures don't trigger the error in that file
  because the function returns the value without dereferencing a
  property). There is no `tsc`/typecheck npm script wired into `npm test`
  or CI yet, so this does not fail `npm test`, `npx vitest run`, or
  `npm run build` (Next's build type-checks app routes, not arbitrary test
  files outside its page/route graph) — flagging for awareness in case a
  future task adds a `typecheck` script.
- `@neondatabase/serverless` remains an unused dependency in
  `package.json` (not removed, since the task only asked to add `pg` /
  `@types/pg`, not to remove the old driver). A later cleanup task may
  want to drop it.
- `drizzle/meta/{0000_snapshot.json,_journal.json}` were committed
  alongside the generated SQL since they are drizzle-kit's standard
  paired output and are needed for correct incremental diffing on the
  next `db:generate` run; the task brief's explicit file list didn't
  mention them but didn't exclude them either.

## Commit

`e89cabc` — `feat(familyshield): db client, postgis partitioning, test harness`
(13 files changed, on branch `feat/kids-app`). `.env` and
`docker-compose.yml` were not staged/committed (verified via
`git show --stat HEAD` and `git status`).
