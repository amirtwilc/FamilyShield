# FamilyShield Backend & Data Core — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the FamilyShield backend & data core — a Next.js + Postgres/PostGIS REST service that lets a parent pair a child device, ingest batched encrypted-in-transit location updates, query current location/history/status, and receive low-battery & offline push alerts, fully documented with Swagger and covered by tests.

**Architecture:** A single Next.js 16 App Router TypeScript service. Drizzle ORM over Neon Postgres (PostGIS enabled). Two auth audiences — parents (JWT) and child devices (opaque bearer token, stored hashed). Location writes are the hot path: `locations` is month-partitioned, current location is denormalized onto `devices` for O(1) reads, history/alerts use cursor pagination. Push via `firebase-admin` (FCM), abstracted behind an injectable sender so tests mock it. OpenAPI generated from the same Zod schemas used for validation.

**Tech Stack:** Next.js 16 (App Router, Node runtime) · TypeScript · Drizzle ORM + `@neondatabase/serverless` · PostGIS · Zod · `@asteasolutions/zod-to-openapi` · `swagger-ui-dist` · `jose` (JWT) · `argon2` · `firebase-admin` · Vitest.

## Global Constraints

- **Slice scope:** backend & data core only. NO native app code, NO geofence-alert firing, NO ML, NO realtime location streaming — those are later slices.
- **Maps/UI constraints** (white+blue palette, MapLibre/OSM not Google, Hebrew RTL dashboard) belong to later UI slices and MUST NOT appear here. This service has no UI except Swagger.
- **Encryption:** TLS in transit (Vercel-terminated) + Neon managed at-rest. Coordinate columns are NOT app-encrypted (must stay PostGIS-queryable).
- **Node runtime** on all routes (`export const runtime = 'nodejs'`) — `argon2` and `firebase-admin` are not edge-compatible.
- **Secrets** only via env vars; never commit. `.env.example` documents every key.
- **Multitenancy:** every parent route authorizes by `parent_id` ownership; every device route by device token. No cross-tenant reads.
- **TDD:** write the failing test first, watch it fail, implement minimally, watch it pass, commit.

---

## File Structure

```
FamilyShield/
  package.json, tsconfig.json, next.config.ts, vitest.config.ts, drizzle.config.ts, .env.example
  src/
    db/
      schema.ts                # Drizzle tables
      client.ts                # db + sql clients
      partitions.ts            # ensureLocationPartition()
    lib/
      http.ts                  # ok()/err() JSON envelope
      validate.ts              # parseBody()/parseQuery()
      pagination.ts            # encodeCursor()/decodeCursor()
      ratelimit.ts             # RateLimiter interface + in-memory impl
      auth/
        password.ts            # argon2 hash/verify
        jwt.ts                 # sign/verify access+refresh
        parent.ts             # requireParent()
        device.ts              # createDeviceToken()/hashToken()/requireDevice()
      alerts/
        fcm.ts                 # PushSender interface + firebase-admin impl + test mock
        engine.ts              # fireLowBatteryIfNeeded(), fireOfflineSweep()
      openapi/
        registry.ts            # shared OpenAPIRegistry + schema registration
        document.ts            # buildOpenApiDocument()
    app/
      api/
        auth/register/route.ts
        auth/login/route.ts
        auth/refresh/route.ts
        pair/route.ts
        locations/route.ts
        device/status/route.ts
        children/route.ts
        children/[id]/route.ts
        children/[id]/pairing-code/route.ts
        children/[id]/location/current/route.ts
        children/[id]/location/history/route.ts
        children/[id]/alerts/route.ts
        children/[id]/zones/route.ts
        children/[id]/zones/[zoneId]/route.ts
        devices/[id]/route.ts
        parent/push-token/route.ts
        cron/offline-sweep/route.ts
        openapi.json/route.ts
        docs/route.ts
  test/
    helpers/db.ts              # test DB + transaction rollback harness
    helpers/factories.ts       # seed parents/children/devices in tests
  drizzle/                     # generated migrations + 0001_locations_partition.sql
```

---

### Task 0: Project scaffold & tooling

**Files:**
- Create: `FamilyShield/package.json`, `tsconfig.json`, `next.config.ts`, `vitest.config.ts`, `.env.example`
- Test: `FamilyShield/test/smoke.test.ts`

**Interfaces:**
- Consumes: nothing.
- Produces: a working `npm test` (Vitest) and `npm run dev` (Next.js).

- [ ] **Step 1: Write the failing test**

`test/smoke.test.ts`:
```ts
import { describe, it, expect } from 'vitest';

describe('smoke', () => {
  it('runs the test runner', () => {
    expect(1 + 1).toBe(2);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd FamilyShield && npx vitest run`
Expected: FAIL — vitest not installed / no config.

- [ ] **Step 3: Scaffold the project**

`package.json`:
```json
{
  "name": "familyshield-backend",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "next dev",
    "build": "next build",
    "start": "next start",
    "test": "vitest run",
    "test:watch": "vitest",
    "db:generate": "drizzle-kit generate",
    "db:migrate": "drizzle-kit migrate"
  },
  "dependencies": {
    "next": "^16.0.0",
    "react": "^19.0.0",
    "react-dom": "^19.0.0",
    "drizzle-orm": "^0.36.0",
    "@neondatabase/serverless": "^0.10.0",
    "zod": "^3.23.0",
    "@asteasolutions/zod-to-openapi": "^7.3.0",
    "swagger-ui-dist": "^5.17.0",
    "jose": "^5.9.0",
    "argon2": "^0.41.0",
    "firebase-admin": "^13.0.0"
  },
  "devDependencies": {
    "typescript": "^5.6.0",
    "@types/node": "^22.0.0",
    "@types/react": "^19.0.0",
    "vitest": "^2.1.0",
    "drizzle-kit": "^0.28.0",
    "dotenv": "^16.4.0"
  }
}
```

`tsconfig.json`:
```json
{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["ES2022", "DOM"],
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "strict": true,
    "noUncheckedIndexedAccess": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "jsx": "preserve",
    "plugins": [{ "name": "next" }],
    "paths": { "@/*": ["./src/*"] }
  },
  "include": ["src", "test", "**/*.ts", "**/*.tsx", ".next/types/**/*.ts"],
  "exclude": ["node_modules"]
}
```

`next.config.ts`:
```ts
import type { NextConfig } from 'next';
const config: NextConfig = { serverExternalPackages: ['argon2', 'firebase-admin'] };
export default config;
```

`vitest.config.ts`:
```ts
import { defineConfig } from 'vitest/config';
import { resolve } from 'node:path';

export default defineConfig({
  test: { environment: 'node', globals: false, fileParallelism: false },
  resolve: { alias: { '@': resolve(__dirname, 'src') } },
});
```

`.env.example`:
```
DATABASE_URL=postgres://user:pass@host/db?sslmode=require
TEST_DATABASE_URL=postgres://user:pass@host/familyshield_test?sslmode=require
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

- [ ] **Step 4: Run test to verify it passes**

Run: `cd FamilyShield && npm install && npx vitest run`
Expected: PASS — 1 test.

- [ ] **Step 5: Commit**

```bash
git add FamilyShield/package.json FamilyShield/tsconfig.json FamilyShield/next.config.ts FamilyShield/vitest.config.ts FamilyShield/.env.example FamilyShield/test/smoke.test.ts
git commit -m "chore(familyshield): scaffold Next.js + Vitest backend"
```

---

### Task 1: Database schema

**Files:**
- Create: `src/db/schema.ts`, `drizzle.config.ts`
- Test: `test/schema.test.ts`

**Interfaces:**
- Produces: Drizzle tables `parents`, `children`, `devices`, `pairingCodes`, `locations`, `safeZones`, `alerts`; the `point` geometry helper. Column names per spec §4.

- [ ] **Step 1: Write the failing test**

`test/schema.test.ts`:
```ts
import { describe, it, expect } from 'vitest';
import * as schema from '@/db/schema';

describe('schema', () => {
  it('exports all tables', () => {
    expect(schema.parents).toBeDefined();
    expect(schema.children).toBeDefined();
    expect(schema.devices).toBeDefined();
    expect(schema.pairingCodes).toBeDefined();
    expect(schema.locations).toBeDefined();
    expect(schema.safeZones).toBeDefined();
    expect(schema.alerts).toBeDefined();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run test/schema.test.ts`
Expected: FAIL — cannot find module `@/db/schema`.

- [ ] **Step 3: Write the schema**

`src/db/schema.ts`:
```ts
import { sql } from 'drizzle-orm';
import {
  pgTable, uuid, text, integer, boolean, timestamp, real, jsonb,
  pgEnum, customType, uniqueIndex, index,
} from 'drizzle-orm/pg-core';

// PostGIS point as WKT in/out; queries use ST_* directly via raw sql.
export const point = customType<{ data: { lat: number; lng: number }; driverData: string }>({
  dataType() { return 'geometry(Point,4326)'; },
  toDriver(v) { return `SRID=4326;POINT(${v.lng} ${v.lat})`; },
});

export const alertType = pgEnum('alert_type', ['low_battery', 'offline']);

export const parents = pgTable('parents', {
  id: uuid('id').primaryKey().defaultRandom(),
  email: text('email').notNull().unique(),
  passwordHash: text('password_hash').notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull(),
});

export const children = pgTable('children', {
  id: uuid('id').primaryKey().defaultRandom(),
  parentId: uuid('parent_id').notNull().references(() => parents.id, { onDelete: 'cascade' }),
  displayName: text('display_name').notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull(),
}, (t) => ({ byParent: index('children_parent_idx').on(t.parentId) }));

export const devices = pgTable('devices', {
  id: uuid('id').primaryKey().defaultRandom(),
  childId: uuid('child_id').notNull().references(() => children.id, { onDelete: 'cascade' }),
  deviceTokenHash: text('device_token_hash').notNull().unique(),
  platform: text('platform').notNull(),
  model: text('model'),
  pairedAt: timestamp('paired_at', { withTimezone: true }).defaultNow().notNull(),
  revokedAt: timestamp('revoked_at', { withTimezone: true }),
  lastSeenAt: timestamp('last_seen_at', { withTimezone: true }),
  batteryLevel: integer('battery_level'),
  isCharging: boolean('is_charging'),
  fcmToken: text('fcm_token'),
  lastLocation: point('last_location'),
  lastLocationAt: timestamp('last_location_at', { withTimezone: true }),
}, (t) => ({ byChild: index('devices_child_idx').on(t.childId) }));

export const pairingCodes = pgTable('pairing_codes', {
  id: uuid('id').primaryKey().defaultRandom(),
  childId: uuid('child_id').notNull().references(() => children.id, { onDelete: 'cascade' }),
  code: text('code').notNull(),
  expiresAt: timestamp('expires_at', { withTimezone: true }).notNull(),
  consumedAt: timestamp('consumed_at', { withTimezone: true }),
  createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull(),
}, (t) => ({ activeCode: index('pairing_active_idx').on(t.code) }));

// NOTE: created as a partitioned table via raw SQL in Task 2's migration.
// Drizzle definition is for query typing only.
export const locations = pgTable('locations', {
  id: uuid('id').defaultRandom().notNull(),
  deviceId: uuid('device_id').notNull(),
  geom: point('geom').notNull(),
  speed: real('speed'),
  accuracy: real('accuracy'),
  batteryLevel: integer('battery_level'),
  recordedAt: timestamp('recorded_at', { withTimezone: true }).notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull(),
}, (t) => ({
  byDeviceTime: index('locations_device_time_idx').on(t.deviceId, t.recordedAt),
  dedupe: uniqueIndex('locations_dedupe_idx').on(t.deviceId, t.recordedAt),
}));

export const safeZones = pgTable('safe_zones', {
  id: uuid('id').primaryKey().defaultRandom(),
  childId: uuid('child_id').notNull().references(() => children.id, { onDelete: 'cascade' }),
  name: text('name').notNull(),
  center: point('center').notNull(),
  radiusM: integer('radius_m').notNull(),
  notifyOnEnter: boolean('notify_on_enter').default(true).notNull(),
  notifyOnExit: boolean('notify_on_exit').default(true).notNull(),
  dwellMinutes: integer('dwell_minutes'),
  createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull(),
});

export const alerts = pgTable('alerts', {
  id: uuid('id').primaryKey().defaultRandom(),
  childId: uuid('child_id').notNull().references(() => children.id, { onDelete: 'cascade' }),
  deviceId: uuid('device_id'),
  type: alertType('type').notNull(),
  payload: jsonb('payload').default(sql`'{}'::jsonb`).notNull(),
  createdAt: timestamp('created_at', { withTimezone: true }).defaultNow().notNull(),
  deliveredAt: timestamp('delivered_at', { withTimezone: true }),
  readAt: timestamp('read_at', { withTimezone: true }),
}, (t) => ({ byChildTime: index('alerts_child_time_idx').on(t.childId, t.createdAt) }));
```

`drizzle.config.ts`:
```ts
import 'dotenv/config';
import { defineConfig } from 'drizzle-kit';

export default defineConfig({
  schema: './src/db/schema.ts',
  out: './drizzle',
  dialect: 'postgresql',
  dbCredentials: { url: process.env.DATABASE_URL! },
});
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run test/schema.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add FamilyShield/src/db/schema.ts FamilyShield/drizzle.config.ts FamilyShield/test/schema.test.ts
git commit -m "feat(familyshield): drizzle schema for data core"
```

---

### Task 2: DB client, PostGIS migration, partitioning, test harness

**Files:**
- Create: `src/db/client.ts`, `src/db/partitions.ts`, `drizzle/0001_locations_partition.sql`, `test/helpers/db.ts`, `test/helpers/factories.ts`
- Test: `test/db.test.ts`

**Interfaces:**
- Produces:
  - `db` (drizzle instance), `pool` (neon Pool) from `client.ts`.
  - `ensureLocationPartition(date: Date): Promise<void>` from `partitions.ts`.
  - `withRollback(fn: (tx) => Promise<void>): Promise<void>` and `resetDb(): Promise<void>` from `test/helpers/db.ts`.
  - `seedParent()`, `seedChild(parentId)`, `seedDevice(childId)` from `factories.ts`.

- [ ] **Step 1: Write the failing test**

`test/db.test.ts`:
```ts
import { describe, it, expect, beforeAll } from 'vitest';
import { db } from '@/db/client';
import { ensureLocationPartition } from '@/db/partitions';
import { sql } from 'drizzle-orm';
import { resetDb } from './helpers/db';
import { seedParent } from './helpers/factories';

beforeAll(async () => { await resetDb(); });

describe('db', () => {
  it('connects and has postgis', async () => {
    const r = await db.execute(sql`SELECT postgis_version() AS v`);
    expect((r.rows[0] as any).v).toBeTruthy();
  });

  it('creates a month partition idempotently', async () => {
    await ensureLocationPartition(new Date('2026-06-15T00:00:00Z'));
    await ensureLocationPartition(new Date('2026-06-20T00:00:00Z')); // same month, no error
    const r = await db.execute(
      sql`SELECT to_regclass('public.locations_2026_06') AS t`);
    expect((r.rows[0] as any).t).toBe('locations_2026_06');
  });

  it('seeds a parent', async () => {
    const p = await seedParent();
    expect(p.id).toBeTruthy();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run test/db.test.ts`
Expected: FAIL — `@/db/client` missing.

- [ ] **Step 3: Write client, migration, partitions, helpers**

`src/db/client.ts`:
```ts
import { Pool } from '@neondatabase/serverless';
import { drizzle } from 'drizzle-orm/neon-serverless';
import * as schema from './schema';

const url = process.env.NODE_ENV === 'test'
  ? process.env.TEST_DATABASE_URL!
  : process.env.DATABASE_URL!;

export const pool = new Pool({ connectionString: url });
export const db = drizzle(pool, { schema });
```

`drizzle/0001_locations_partition.sql` (run by `resetDb` and in deploy after `drizzle-kit migrate`):
```sql
CREATE EXTENSION IF NOT EXISTS postgis;

-- locations is RANGE-partitioned by recorded_at (monthly). PK must include the
-- partition key, so PK is (id, recorded_at).
DROP TABLE IF EXISTS locations CASCADE;
CREATE TABLE locations (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  device_id uuid NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  geom geometry(Point,4326) NOT NULL,
  speed real,
  accuracy real,
  battery_level integer,
  recorded_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (id, recorded_at)
) PARTITION BY RANGE (recorded_at);

CREATE UNIQUE INDEX locations_dedupe_idx ON locations (device_id, recorded_at);
CREATE INDEX locations_device_time_idx ON locations (device_id, recorded_at DESC);
CREATE INDEX locations_geom_idx ON locations USING GIST (geom);
```

`src/db/partitions.ts`:
```ts
import { sql } from 'drizzle-orm';
import { db } from './client';

/** Creates the monthly partition covering `date` if it does not exist. */
export async function ensureLocationPartition(date: Date): Promise<void> {
  const y = date.getUTCFullYear();
  const m = date.getUTCMonth(); // 0-based
  const start = new Date(Date.UTC(y, m, 1));
  const end = new Date(Date.UTC(y, m + 1, 1));
  const name = `locations_${y}_${String(m + 1).padStart(2, '0')}`;
  const iso = (d: Date) => d.toISOString().slice(0, 10);
  await db.execute(sql.raw(
    `CREATE TABLE IF NOT EXISTS ${name} PARTITION OF locations ` +
    `FOR VALUES FROM ('${iso(start)}') TO ('${iso(end)}');`,
  ));
}
```

`test/helpers/db.ts`:
```ts
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { sql } from 'drizzle-orm';
import { db } from '@/db/client';

/** Drops & recreates the full schema for a clean test database. */
export async function resetDb(): Promise<void> {
  await db.execute(sql`DROP SCHEMA public CASCADE; CREATE SCHEMA public;`);
  // Apply generated drizzle migrations then the partition override.
  // For tests we apply the two known SQL files in order.
  const gen = resolve(process.cwd(), 'drizzle');
  // 0000 = drizzle-kit generated baseline; 0001 = partition override.
  for (const f of ['0000_init.sql', '0001_locations_partition.sql']) {
    const body = readFileSync(resolve(gen, f), 'utf8');
    await db.execute(sql.raw(body));
  }
}
```

> Note for implementer: after Task 1, run `npm run db:generate` once to produce `drizzle/0000_init.sql`. If drizzle names it differently, update the filename array above to match.

`test/helpers/factories.ts`:
```ts
import { db } from '@/db/client';
import { parents, children, devices } from '@/db/schema';
import { hashToken } from '@/lib/auth/device';

export async function seedParent(email = `p${Date.now()}@t.io`) {
  const [p] = await db.insert(parents)
    .values({ email, passwordHash: 'x' }).returning();
  return p;
}
export async function seedChild(parentId: string, displayName = 'Kid') {
  const [c] = await db.insert(children)
    .values({ parentId, displayName }).returning();
  return c;
}
export async function seedDevice(childId: string, token = `tok-${Date.now()}`) {
  const [d] = await db.insert(devices)
    .values({ childId, deviceTokenHash: hashToken(token), platform: 'android' })
    .returning();
  return { device: d, token };
}
```

> `factories.ts` imports `hashToken` from Task 4b. If implementing strictly in order, stub `hashToken` import until Task 4b lands, or reorder so Task 4b precedes this. Recommended: implement `src/lib/auth/device.ts`'s `hashToken` (a 3-line sha256) as part of this task to unblock factories.

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run test/db.test.ts`
Expected: PASS (requires `TEST_DATABASE_URL` pointing at a Postgres+PostGIS test DB).

- [ ] **Step 5: Commit**

```bash
git add FamilyShield/src/db/client.ts FamilyShield/src/db/partitions.ts FamilyShield/drizzle/0001_locations_partition.sql FamilyShield/test/helpers FamilyShield/test/db.test.ts
git commit -m "feat(familyshield): db client, postgis partitioning, test harness"
```

---

### Task 3: HTTP envelope, validation, pagination helpers

**Files:**
- Create: `src/lib/http.ts`, `src/lib/validate.ts`, `src/lib/pagination.ts`
- Test: `test/lib/http.test.ts`

**Interfaces:**
- Produces:
  - `ok(data, status = 200): Response`, `err(code, message, status, details?): Response` from `http.ts`.
  - `parseBody<T>(req, schema): Promise<{ data: T } | { response: Response }>` and `parseQuery<T>(url, schema)` from `validate.ts`.
  - `encodeCursor(v: { recordedAt: string; id: string }): string`, `decodeCursor(s): { recordedAt: string; id: string } | null` from `pagination.ts`.

- [ ] **Step 1: Write the failing test**

`test/lib/http.test.ts`:
```ts
import { describe, it, expect } from 'vitest';
import { z } from 'zod';
import { ok, err } from '@/lib/http';
import { parseBody } from '@/lib/validate';
import { encodeCursor, decodeCursor } from '@/lib/pagination';

describe('http helpers', () => {
  it('ok wraps data', async () => {
    const r = ok({ a: 1 }, 201);
    expect(r.status).toBe(201);
    expect(await r.json()).toEqual({ a: 1 });
  });

  it('err uses the envelope', async () => {
    const r = err('bad', 'nope', 400, { field: 'x' });
    expect(r.status).toBe(400);
    expect(await r.json()).toEqual({ error: { code: 'bad', message: 'nope', details: { field: 'x' } } });
  });

  it('parseBody returns 400 on invalid', async () => {
    const req = new Request('http://t/', { method: 'POST', body: '{}' });
    const out = await parseBody(req, z.object({ x: z.string() }));
    expect('response' in out && out.response.status).toBe(400);
  });

  it('cursor round-trips', () => {
    const c = encodeCursor({ recordedAt: '2026-06-19T00:00:00Z', id: 'abc' });
    expect(decodeCursor(c)).toEqual({ recordedAt: '2026-06-19T00:00:00Z', id: 'abc' });
    expect(decodeCursor('garbage!!')).toBeNull();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run test/lib/http.test.ts`
Expected: FAIL — modules missing.

- [ ] **Step 3: Implement helpers**

`src/lib/http.ts`:
```ts
export function ok(data: unknown, status = 200): Response {
  return Response.json(data, { status });
}
export function err(code: string, message: string, status: number, details?: unknown): Response {
  return Response.json({ error: { code, message, ...(details !== undefined ? { details } : {}) } }, { status });
}
```

`src/lib/validate.ts`:
```ts
import type { ZodSchema } from 'zod';
import { err } from './http';

export async function parseBody<T>(req: Request, schema: ZodSchema<T>): Promise<{ data: T } | { response: Response }> {
  let body: unknown;
  try { body = await req.json(); } catch { return { response: err('invalid_json', 'Body is not valid JSON', 400) }; }
  const r = schema.safeParse(body);
  if (!r.success) return { response: err('validation_error', 'Invalid request body', 400, r.error.flatten()) };
  return { data: r.data };
}

export function parseQuery<T>(url: URL, schema: ZodSchema<T>): { data: T } | { response: Response } {
  const r = schema.safeParse(Object.fromEntries(url.searchParams));
  if (!r.success) return { response: err('validation_error', 'Invalid query', 400, r.error.flatten()) };
  return { data: r.data };
}
```

`src/lib/pagination.ts`:
```ts
export function encodeCursor(v: { recordedAt: string; id: string }): string {
  return Buffer.from(JSON.stringify(v), 'utf8').toString('base64url');
}
export function decodeCursor(s: string): { recordedAt: string; id: string } | null {
  try {
    const v = JSON.parse(Buffer.from(s, 'base64url').toString('utf8'));
    if (typeof v?.recordedAt === 'string' && typeof v?.id === 'string') return v;
    return null;
  } catch { return null; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run test/lib/http.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add FamilyShield/src/lib/http.ts FamilyShield/src/lib/validate.ts FamilyShield/src/lib/pagination.ts FamilyShield/test/lib/http.test.ts
git commit -m "feat(familyshield): http envelope, validation, cursor helpers"
```

---

### Task 4: Auth primitives — password, JWT, device token

**Files:**
- Create: `src/lib/auth/password.ts`, `src/lib/auth/jwt.ts`, `src/lib/auth/device.ts`
- Test: `test/lib/auth.test.ts`

**Interfaces:**
- Produces:
  - `hashPassword(pw): Promise<string>`, `verifyPassword(hash, pw): Promise<boolean>`.
  - `signAccess(parentId): Promise<string>`, `signRefresh(parentId): Promise<string>`, `verifyAccess(token): Promise<string>` (returns parentId, throws on invalid), `verifyRefresh(token): Promise<string>`.
  - `createDeviceToken(): { token: string; hash: string }`, `hashToken(token): string`.

- [ ] **Step 1: Write the failing test**

`test/lib/auth.test.ts`:
```ts
import { describe, it, expect } from 'vitest';
import { hashPassword, verifyPassword } from '@/lib/auth/password';
import { signAccess, verifyAccess } from '@/lib/auth/jwt';
import { createDeviceToken, hashToken } from '@/lib/auth/device';

describe('auth primitives', () => {
  it('hashes and verifies passwords', async () => {
    const h = await hashPassword('s3cret');
    expect(await verifyPassword(h, 's3cret')).toBe(true);
    expect(await verifyPassword(h, 'wrong')).toBe(false);
  });

  it('signs and verifies access tokens', async () => {
    const t = await signAccess('parent-123');
    expect(await verifyAccess(t)).toBe('parent-123');
    await expect(verifyAccess('not.a.jwt')).rejects.toThrow();
  });

  it('device token hash is deterministic and matches', () => {
    const { token, hash } = createDeviceToken();
    expect(hashToken(token)).toBe(hash);
    expect(hash).not.toBe(token);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run test/lib/auth.test.ts`
Expected: FAIL — modules missing.

- [ ] **Step 3: Implement**

`src/lib/auth/password.ts`:
```ts
import argon2 from 'argon2';
export const hashPassword = (pw: string) => argon2.hash(pw);
export const verifyPassword = (hash: string, pw: string) => argon2.verify(hash, pw).catch(() => false);
```

`src/lib/auth/jwt.ts`:
```ts
import { SignJWT, jwtVerify } from 'jose';

const enc = (s: string) => new TextEncoder().encode(s);
const accessKey = () => enc(process.env.JWT_SECRET!);
const refreshKey = () => enc(process.env.JWT_REFRESH_SECRET!);

async function sign(parentId: string, key: Uint8Array, ttl: string): Promise<string> {
  return new SignJWT({ sub: parentId })
    .setProtectedHeader({ alg: 'HS256' })
    .setIssuedAt().setExpirationTime(ttl).sign(key);
}
export const signAccess = (parentId: string) => sign(parentId, accessKey(), '15m');
export const signRefresh = (parentId: string) => sign(parentId, refreshKey(), '30d');

async function verify(token: string, key: Uint8Array): Promise<string> {
  const { payload } = await jwtVerify(token, key);
  if (typeof payload.sub !== 'string') throw new Error('no sub');
  return payload.sub;
}
export const verifyAccess = (t: string) => verify(t, accessKey());
export const verifyRefresh = (t: string) => verify(t, refreshKey());
```

`src/lib/auth/device.ts` (only the crypto parts here; `requireDevice` added in Task 4b):
```ts
import { randomBytes, createHash } from 'node:crypto';

export function hashToken(token: string): string {
  return createHash('sha256').update(token).digest('hex');
}
export function createDeviceToken(): { token: string; hash: string } {
  const token = randomBytes(32).toString('base64url');
  return { token, hash: hashToken(token) };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run test/lib/auth.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add FamilyShield/src/lib/auth FamilyShield/test/lib/auth.test.ts
git commit -m "feat(familyshield): password, jwt, device-token primitives"
```

---

### Task 4b: Auth guards — requireParent / requireDevice

**Files:**
- Create: `src/lib/auth/parent.ts`; Modify: `src/lib/auth/device.ts`
- Test: `test/lib/guards.test.ts`

**Interfaces:**
- Consumes: `verifyAccess` (Task 4), `hashToken` (Task 4), `db`, `devices` (Tasks 1–2).
- Produces:
  - `requireParent(req): Promise<{ parentId: string } | { response: Response }>`.
  - `requireDevice(req): Promise<{ device: typeof devices.$inferSelect } | { response: Response }>` (rejects revoked devices).

- [ ] **Step 1: Write the failing test**

`test/lib/guards.test.ts`:
```ts
import { describe, it, expect, beforeAll } from 'vitest';
import { resetDb } from '../helpers/db';
import { seedParent, seedChild, seedDevice } from '../helpers/factories';
import { signAccess } from '@/lib/auth/jwt';
import { requireParent } from '@/lib/auth/parent';
import { requireDevice } from '@/lib/auth/device';

beforeAll(async () => { await resetDb(); });
const bearer = (t: string) => new Request('http://t/', { headers: { authorization: `Bearer ${t}` } });

describe('guards', () => {
  it('requireParent accepts a valid token', async () => {
    const p = await seedParent();
    const out = await requireParent(bearer(await signAccess(p.id)));
    expect('parentId' in out && out.parentId).toBe(p.id);
  });
  it('requireParent rejects missing token with 401', async () => {
    const out = await requireParent(new Request('http://t/'));
    expect('response' in out && out.response.status).toBe(401);
  });
  it('requireDevice accepts a paired device, rejects revoked', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    const { token } = await seedDevice(c.id);
    const out = await requireDevice(bearer(token));
    expect('device' in out).toBe(true);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run test/lib/guards.test.ts`
Expected: FAIL — `requireParent`/`requireDevice` missing.

- [ ] **Step 3: Implement guards**

`src/lib/auth/parent.ts`:
```ts
import { verifyAccess } from './jwt';
import { err } from '../http';

function bearer(req: Request): string | null {
  const h = req.headers.get('authorization') ?? '';
  return h.startsWith('Bearer ') ? h.slice(7) : null;
}
export async function requireParent(req: Request): Promise<{ parentId: string } | { response: Response }> {
  const t = bearer(req);
  if (!t) return { response: err('unauthorized', 'Missing bearer token', 401) };
  try { return { parentId: await verifyAccess(t) }; }
  catch { return { response: err('unauthorized', 'Invalid token', 401) }; }
}
```

Append to `src/lib/auth/device.ts`:
```ts
import { eq, and, isNull } from 'drizzle-orm';
import { db } from '../../db/client';
import { devices } from '../../db/schema';
import { err } from '../http';

function bearer(req: Request): string | null {
  const h = req.headers.get('authorization') ?? '';
  return h.startsWith('Bearer ') ? h.slice(7) : null;
}
export async function requireDevice(req: Request): Promise<{ device: typeof devices.$inferSelect } | { response: Response }> {
  const t = bearer(req);
  if (!t) return { response: err('unauthorized', 'Missing device token', 401) };
  const [d] = await db.select().from(devices)
    .where(and(eq(devices.deviceTokenHash, hashToken(t)), isNull(devices.revokedAt)));
  if (!d) return { response: err('unauthorized', 'Unknown or revoked device', 401) };
  return { device: d };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run test/lib/guards.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add FamilyShield/src/lib/auth/parent.ts FamilyShield/src/lib/auth/device.ts FamilyShield/test/lib/guards.test.ts
git commit -m "feat(familyshield): parent + device auth guards"
```

---

### Task 5: Parent register / login / refresh

**Files:**
- Create: `src/app/api/auth/register/route.ts`, `src/app/api/auth/login/route.ts`, `src/app/api/auth/refresh/route.ts`, `src/lib/schemas/auth.ts`
- Test: `test/api/auth.test.ts`

**Interfaces:**
- Consumes: `hashPassword`/`verifyPassword`, `signAccess`/`signRefresh`/`verifyRefresh`, `parseBody`, `ok`/`err`, `db`, `parents`.
- Produces: `registerSchema`, `loginSchema`, `refreshSchema` (Zod) for reuse by OpenAPI (Task 18). Response shape `{ accessToken, refreshToken }`.

- [ ] **Step 1: Write the failing test**

`test/api/auth.test.ts`:
```ts
import { describe, it, expect, beforeAll } from 'vitest';
import { resetDb } from '../helpers/db';
import { POST as register } from '@/app/api/auth/register/route';
import { POST as login } from '@/app/api/auth/login/route';

beforeAll(async () => { await resetDb(); });
const post = (body: unknown) => new Request('http://t/', { method: 'POST', body: JSON.stringify(body) });

describe('auth api', () => {
  it('registers and logs in', async () => {
    const r1 = await register(post({ email: 'a@b.io', password: 'password123' }));
    expect(r1.status).toBe(201);
    const t1 = await r1.json();
    expect(t1.accessToken).toBeTruthy();

    const r2 = await login(post({ email: 'a@b.io', password: 'password123' }));
    expect(r2.status).toBe(200);

    const r3 = await login(post({ email: 'a@b.io', password: 'wrong' }));
    expect(r3.status).toBe(401);
  });

  it('rejects duplicate email', async () => {
    await register(post({ email: 'dup@b.io', password: 'password123' }));
    const r = await register(post({ email: 'dup@b.io', password: 'password123' }));
    expect(r.status).toBe(409);
  });

  it('rejects short password', async () => {
    const r = await register(post({ email: 'x@b.io', password: 'short' }));
    expect(r.status).toBe(400);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run test/api/auth.test.ts`
Expected: FAIL — routes missing.

- [ ] **Step 3: Implement schemas + routes**

`src/lib/schemas/auth.ts`:
```ts
import { z } from 'zod';
export const registerSchema = z.object({ email: z.string().email(), password: z.string().min(8) });
export const loginSchema = registerSchema;
export const refreshSchema = z.object({ refreshToken: z.string().min(1) });
export const tokenPairSchema = z.object({ accessToken: z.string(), refreshToken: z.string() });
```

`src/app/api/auth/register/route.ts`:
```ts
import { eq } from 'drizzle-orm';
import { db } from '@/db/client';
import { parents } from '@/db/schema';
import { parseBody } from '@/lib/validate';
import { ok, err } from '@/lib/http';
import { hashPassword } from '@/lib/auth/password';
import { signAccess, signRefresh } from '@/lib/auth/jwt';
import { registerSchema } from '@/lib/schemas/auth';

export const runtime = 'nodejs';

export async function POST(req: Request) {
  const p = await parseBody(req, registerSchema);
  if ('response' in p) return p.response;
  const existing = await db.select().from(parents).where(eq(parents.email, p.data.email));
  if (existing.length) return err('email_taken', 'Email already registered', 409);
  const [row] = await db.insert(parents)
    .values({ email: p.data.email, passwordHash: await hashPassword(p.data.password) })
    .returning();
  return ok({ accessToken: await signAccess(row.id), refreshToken: await signRefresh(row.id) }, 201);
}
```

`src/app/api/auth/login/route.ts`:
```ts
import { eq } from 'drizzle-orm';
import { db } from '@/db/client';
import { parents } from '@/db/schema';
import { parseBody } from '@/lib/validate';
import { ok, err } from '@/lib/http';
import { verifyPassword } from '@/lib/auth/password';
import { signAccess, signRefresh } from '@/lib/auth/jwt';
import { loginSchema } from '@/lib/schemas/auth';

export const runtime = 'nodejs';

export async function POST(req: Request) {
  const p = await parseBody(req, loginSchema);
  if ('response' in p) return p.response;
  const [row] = await db.select().from(parents).where(eq(parents.email, p.data.email));
  if (!row || !(await verifyPassword(row.passwordHash, p.data.password)))
    return err('invalid_credentials', 'Invalid email or password', 401);
  return ok({ accessToken: await signAccess(row.id), refreshToken: await signRefresh(row.id) });
}
```

`src/app/api/auth/refresh/route.ts`:
```ts
import { parseBody } from '@/lib/validate';
import { ok, err } from '@/lib/http';
import { verifyRefresh, signAccess, signRefresh } from '@/lib/auth/jwt';
import { refreshSchema } from '@/lib/schemas/auth';

export const runtime = 'nodejs';

export async function POST(req: Request) {
  const p = await parseBody(req, refreshSchema);
  if ('response' in p) return p.response;
  try {
    const parentId = await verifyRefresh(p.data.refreshToken);
    return ok({ accessToken: await signAccess(parentId), refreshToken: await signRefresh(parentId) });
  } catch { return err('unauthorized', 'Invalid refresh token', 401); }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run test/api/auth.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add FamilyShield/src/app/api/auth FamilyShield/src/lib/schemas/auth.ts FamilyShield/test/api/auth.test.ts
git commit -m "feat(familyshield): parent register/login/refresh endpoints"
```

---

### Task 6: Children CRUD + pairing-code generation

**Files:**
- Create: `src/app/api/children/route.ts`, `src/app/api/children/[id]/route.ts`, `src/app/api/children/[id]/pairing-code/route.ts`, `src/lib/schemas/children.ts`, `src/lib/ownership.ts`
- Test: `test/api/children.test.ts`

**Interfaces:**
- Consumes: `requireParent`, `parseBody`, `ok`/`err`, `db`.
- Produces:
  - `assertChildOwned(parentId, childId): Promise<typeof children.$inferSelect | null>` from `ownership.ts`.
  - `createChildSchema` (`{ displayName }`), pairing-code response `{ code, expiresAt }`.

- [ ] **Step 1: Write the failing test**

`test/api/children.test.ts`:
```ts
import { describe, it, expect, beforeAll } from 'vitest';
import { resetDb } from '../helpers/db';
import { seedParent } from '../helpers/factories';
import { signAccess } from '@/lib/auth/jwt';
import { POST as createChild, GET as listChildren } from '@/app/api/children/route';
import { POST as genCode } from '@/app/api/children/[id]/pairing-code/route';

beforeAll(async () => { await resetDb(); });
const auth = (t: string, body?: unknown) => new Request('http://t/', {
  method: 'POST', headers: { authorization: `Bearer ${t}` },
  body: body ? JSON.stringify(body) : undefined,
});

describe('children api', () => {
  it('creates, lists, and generates a pairing code', async () => {
    const p = await seedParent(); const tok = await signAccess(p.id);
    const r1 = await createChild(auth(tok, { displayName: 'Mia' }));
    expect(r1.status).toBe(201);
    const child = await r1.json();

    const r2 = await listChildren(new Request('http://t/', { headers: { authorization: `Bearer ${tok}` } }));
    expect((await r2.json()).children).toHaveLength(1);

    const r3 = await genCode(auth(tok), { params: Promise.resolve({ id: child.id }) });
    const code = await r3.json();
    expect(code.code).toMatch(/^\d{6}$/);
  });

  it('rejects unauthenticated', async () => {
    const r = await createChild(new Request('http://t/', { method: 'POST', body: '{}' }));
    expect(r.status).toBe(401);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run test/api/children.test.ts`
Expected: FAIL — routes missing.

- [ ] **Step 3: Implement**

`src/lib/schemas/children.ts`:
```ts
import { z } from 'zod';
export const createChildSchema = z.object({ displayName: z.string().min(1).max(80) });
```

`src/lib/ownership.ts`:
```ts
import { and, eq } from 'drizzle-orm';
import { db } from '@/db/client';
import { children } from '@/db/schema';

export async function assertChildOwned(parentId: string, childId: string) {
  const [c] = await db.select().from(children)
    .where(and(eq(children.id, childId), eq(children.parentId, parentId)));
  return c ?? null;
}
```

`src/app/api/children/route.ts`:
```ts
import { eq } from 'drizzle-orm';
import { db } from '@/db/client';
import { children, devices } from '@/db/schema';
import { requireParent } from '@/lib/auth/parent';
import { parseBody } from '@/lib/validate';
import { ok } from '@/lib/http';
import { createChildSchema } from '@/lib/schemas/children';

export const runtime = 'nodejs';

export async function POST(req: Request) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const p = await parseBody(req, createChildSchema); if ('response' in p) return p.response;
  const [row] = await db.insert(children)
    .values({ parentId: a.parentId, displayName: p.data.displayName }).returning();
  return ok(row, 201);
}

export async function GET(req: Request) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const kids = await db.select().from(children).where(eq(children.parentId, a.parentId));
  const result = await Promise.all(kids.map(async (k) => ({
    ...k,
    devices: await db.select().from(devices).where(eq(devices.childId, k.id)),
  })));
  return ok({ children: result });
}
```

`src/app/api/children/[id]/route.ts`:
```ts
import { requireParent } from '@/lib/auth/parent';
import { assertChildOwned } from '@/lib/ownership';
import { ok, err } from '@/lib/http';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string }> };

export async function GET(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  const child = await assertChildOwned(a.parentId, id);
  if (!child) return err('not_found', 'Child not found', 404);
  return ok(child);
}
```

`src/app/api/children/[id]/pairing-code/route.ts`:
```ts
import { db } from '@/db/client';
import { pairingCodes } from '@/db/schema';
import { requireParent } from '@/lib/auth/parent';
import { assertChildOwned } from '@/lib/ownership';
import { ok, err } from '@/lib/http';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string }> };

export async function POST(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const ttlMin = Number(process.env.PAIRING_CODE_TTL_MIN ?? 10);
  const code = String(Math.floor(100000 + Math.random() * 900000));
  const expiresAt = new Date(Date.now() + ttlMin * 60_000);
  await db.insert(pairingCodes).values({ childId: id, code, expiresAt });
  return ok({ code, expiresAt: expiresAt.toISOString() }, 201);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run test/api/children.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add FamilyShield/src/app/api/children FamilyShield/src/lib/schemas/children.ts FamilyShield/src/lib/ownership.ts FamilyShield/test/api/children.test.ts
git commit -m "feat(familyshield): children CRUD + pairing code generation"
```

---

### Task 7: Device pairing (`POST /api/pair`)

**Files:**
- Create: `src/app/api/pair/route.ts`, `src/lib/schemas/pair.ts`
- Test: `test/api/pair.test.ts`

**Interfaces:**
- Consumes: `createDeviceToken`, `parseBody`, `db`, `pairingCodes`, `devices`.
- Produces: response `{ deviceToken, childId }`. Consumes a code atomically (single-use, TTL-checked).

- [ ] **Step 1: Write the failing test**

`test/api/pair.test.ts`:
```ts
import { describe, it, expect, beforeAll } from 'vitest';
import { resetDb } from '../helpers/db';
import { seedParent, seedChild } from '../helpers/factories';
import { db } from '@/db/client';
import { pairingCodes } from '@/db/schema';
import { POST as pair } from '@/app/api/pair/route';

beforeAll(async () => { await resetDb(); });
const post = (body: unknown) => new Request('http://t/', { method: 'POST', body: JSON.stringify(body) });

async function makeCode(childId: string, code = '123456', minsValid = 10) {
  await db.insert(pairingCodes).values({ childId, code, expiresAt: new Date(Date.now() + minsValid * 60000) });
}

describe('pairing', () => {
  it('pairs with a valid code, single-use', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    await makeCode(c.id, '111111');
    const r = await pair(post({ code: '111111', platform: 'android', model: 'Pixel' }));
    expect(r.status).toBe(201);
    expect((await r.json()).deviceToken).toBeTruthy();
    const r2 = await pair(post({ code: '111111', platform: 'android' }));
    expect(r2.status).toBe(400); // already consumed
  });

  it('rejects expired codes', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    await makeCode(c.id, '222222', -1);
    const r = await pair(post({ code: '222222', platform: 'android' }));
    expect(r.status).toBe(400);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run test/api/pair.test.ts`
Expected: FAIL — route missing.

- [ ] **Step 3: Implement**

`src/lib/schemas/pair.ts`:
```ts
import { z } from 'zod';
export const pairSchema = z.object({
  code: z.string().regex(/^\d{6}$/),
  platform: z.string().min(1).max(40),
  model: z.string().max(120).optional(),
});
```

`src/app/api/pair/route.ts`:
```ts
import { and, eq, gt, isNull } from 'drizzle-orm';
import { db } from '@/db/client';
import { pairingCodes, devices } from '@/db/schema';
import { parseBody } from '@/lib/validate';
import { ok, err } from '@/lib/http';
import { createDeviceToken } from '@/lib/auth/device';
import { pairSchema } from '@/lib/schemas/pair';

export const runtime = 'nodejs';

export async function POST(req: Request) {
  const p = await parseBody(req, pairSchema); if ('response' in p) return p.response;
  // Atomically consume an unexpired, unconsumed code.
  const [claimed] = await db.update(pairingCodes)
    .set({ consumedAt: new Date() })
    .where(and(
      eq(pairingCodes.code, p.data.code),
      isNull(pairingCodes.consumedAt),
      gt(pairingCodes.expiresAt, new Date()),
    )).returning();
  if (!claimed) return err('invalid_code', 'Code is invalid, expired, or already used', 400);
  const { token, hash } = createDeviceToken();
  await db.insert(devices).values({
    childId: claimed.childId, deviceTokenHash: hash,
    platform: p.data.platform, model: p.data.model ?? null, lastSeenAt: new Date(),
  });
  return ok({ deviceToken: token, childId: claimed.childId }, 201);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run test/api/pair.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add FamilyShield/src/app/api/pair FamilyShield/src/lib/schemas/pair.ts FamilyShield/test/api/pair.test.ts
git commit -m "feat(familyshield): device pairing endpoint"
```

---

### Task 8: FCM sender abstraction + alert engine (low-battery)

**Files:**
- Create: `src/lib/alerts/fcm.ts`, `src/lib/alerts/engine.ts`
- Test: `test/lib/alerts.test.ts`

**Interfaces:**
- Produces:
  - `PushSender` interface `{ send(fcmToken: string, title: string, body: string, data?: Record<string,string>): Promise<boolean> }`.
  - `getSender(): PushSender` (firebase-admin impl, lazy), `setSender(s)`/`resetSender()` for tests.
  - `fireLowBatteryIfNeeded(device): Promise<void>` — inserts an `alerts` row + pushes, debounced by `LOW_BATTERY_COOLDOWN_MIN`.

- [ ] **Step 1: Write the failing test**

`test/lib/alerts.test.ts`:
```ts
import { describe, it, expect, beforeAll, beforeEach } from 'vitest';
import { resetDb } from '../helpers/db';
import { seedParent, seedChild, seedDevice } from '../helpers/factories';
import { db } from '@/db/client';
import { devices, alerts } from '@/db/schema';
import { eq } from 'drizzle-orm';
import { fireLowBatteryIfNeeded } from '@/lib/alerts/engine';
import { setSender, resetSender } from '@/lib/alerts/fcm';

let sent = 0;
beforeAll(async () => { await resetDb(); });
beforeEach(() => { sent = 0; setSender({ async send() { sent++; return true; } }); });

describe('low-battery alert', () => {
  it('fires once below threshold then debounces', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    const { device } = await seedDevice(c.id);
    await db.update(devices).set({ batteryLevel: 10, isCharging: false }).where(eq(devices.id, device.id));
    const fresh = (await db.select().from(devices).where(eq(devices.id, device.id)))[0];

    await fireLowBatteryIfNeeded(fresh);
    await fireLowBatteryIfNeeded(fresh); // debounced
    const rows = await db.select().from(alerts).where(eq(alerts.childId, c.id));
    expect(rows).toHaveLength(1);
    expect(sent).toBe(1);
    resetSender();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run test/lib/alerts.test.ts`
Expected: FAIL — modules missing.

- [ ] **Step 3: Implement**

`src/lib/alerts/fcm.ts`:
```ts
export interface PushSender {
  send(fcmToken: string, title: string, body: string, data?: Record<string, string>): Promise<boolean>;
}

let override: PushSender | null = null;
export function setSender(s: PushSender) { override = s; }
export function resetSender() { override = null; }

class FirebaseSender implements PushSender {
  async send(fcmToken: string, title: string, body: string, data?: Record<string, string>) {
    if (!process.env.FCM_SERVICE_ACCOUNT_JSON) return false;
    const admin = await import('firebase-admin');
    if (!admin.apps.length) {
      admin.initializeApp({ credential: admin.credential.cert(JSON.parse(process.env.FCM_SERVICE_ACCOUNT_JSON)) });
    }
    await admin.messaging().send({ token: fcmToken, notification: { title, body }, data });
    return true;
  }
}
export function getSender(): PushSender { return override ?? new FirebaseSender(); }
```

`src/lib/alerts/engine.ts`:
```ts
import { and, eq, gt } from 'drizzle-orm';
import { db } from '../../db/client';
import { devices, children, parents, alerts } from '../../db/schema';
import { getSender } from './fcm';

type Device = typeof devices.$inferSelect;

async function parentFcmFor(childId: string): Promise<string | null> {
  // parent.fcmToken added in Task 11; until then this returns null safely.
  const [row] = await db.select({ token: parents.fcmToken })
    .from(children).innerJoin(parents, eq(children.parentId, parents.id))
    .where(eq(children.id, childId));
  return row?.token ?? null;
}

export async function fireLowBatteryIfNeeded(device: Device): Promise<void> {
  const threshold = Number(process.env.LOW_BATTERY_THRESHOLD ?? 15);
  const cooldownMin = Number(process.env.LOW_BATTERY_COOLDOWN_MIN ?? 60);
  if (device.batteryLevel == null || device.batteryLevel > threshold || device.isCharging) return;

  const since = new Date(Date.now() - cooldownMin * 60_000);
  const [recent] = await db.select().from(alerts).where(and(
    eq(alerts.deviceId, device.id), eq(alerts.type, 'low_battery'), gt(alerts.createdAt, since),
  ));
  if (recent) return;

  const [row] = await db.insert(alerts).values({
    childId: device.childId, deviceId: device.id, type: 'low_battery',
    payload: { batteryLevel: device.batteryLevel },
  }).returning();

  const fcm = await parentFcmFor(device.childId);
  if (fcm && await getSender().send(fcm, 'Low battery', `Battery at ${device.batteryLevel}%`, { type: 'low_battery' })) {
    await db.update(alerts).set({ deliveredAt: new Date() }).where(eq(alerts.id, row.id));
  }
}
```

> `parents.fcmToken` is added in Task 11. To keep this task green in isolation, add the column now via a one-line schema edit (`fcmToken: text('fcm_token')` on `parents`) and regenerate the migration; Task 11 then only adds the endpoint. Apply that schema edit as part of this task.

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run test/lib/alerts.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add FamilyShield/src/lib/alerts FamilyShield/src/db/schema.ts FamilyShield/test/lib/alerts.test.ts
git commit -m "feat(familyshield): FCM sender + low-battery alert engine"
```

---

### Task 9: Location batch ingestion (`POST /api/locations`)

**Files:**
- Create: `src/app/api/locations/route.ts`, `src/lib/schemas/locations.ts`
- Test: `test/api/locations.test.ts`

**Interfaces:**
- Consumes: `requireDevice`, `parseBody`, `ensureLocationPartition`, `fireLowBatteryIfNeeded`, raw SQL insert with PostGIS, `MAX_LOCATION_BATCH`.
- Produces: response `{ inserted: number }`. Updates `devices.lastLocation`/`lastLocationAt`/`lastSeenAt`/battery. Idempotent on `(device_id, recorded_at)`.

- [ ] **Step 1: Write the failing test**

`test/api/locations.test.ts`:
```ts
import { describe, it, expect, beforeAll, beforeEach } from 'vitest';
import { resetDb } from '../helpers/db';
import { seedParent, seedChild, seedDevice } from '../helpers/factories';
import { setSender } from '@/lib/alerts/fcm';
import { db } from '@/db/client';
import { devices, locations } from '@/db/schema';
import { eq } from 'drizzle-orm';
import { POST as upload } from '@/app/api/locations/route';

beforeAll(async () => { await resetDb(); });
beforeEach(() => setSender({ async send() { return true; } }));
const post = (token: string, body: unknown) => new Request('http://t/', {
  method: 'POST', headers: { authorization: `Bearer ${token}` }, body: JSON.stringify(body),
});

describe('locations ingestion', () => {
  it('ingests a batch, denormalizes last location, is idempotent', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    const { token, device } = await seedDevice(c.id);
    const points = [
      { lat: 32.07, lng: 34.78, recorded_at: '2026-06-19T08:00:00Z', battery_level: 90 },
      { lat: 32.08, lng: 34.79, recorded_at: '2026-06-19T08:05:00Z', battery_level: 88 },
    ];
    const r1 = await upload(post(token, { points }));
    expect((await r1.json()).inserted).toBe(2);

    // idempotent re-upload
    const r2 = await upload(post(token, { points }));
    expect((await r2.json()).inserted).toBe(0);

    const rows = await db.select().from(locations).where(eq(locations.deviceId, device.id));
    expect(rows).toHaveLength(2);
    const [d] = await db.select().from(devices).where(eq(devices.id, device.id));
    expect(d.lastLocationAt?.toISOString()).toBe('2026-06-19T08:05:00.000Z');
  });

  it('rejects unauthenticated', async () => {
    const r = await upload(new Request('http://t/', { method: 'POST', body: '{}' }));
    expect(r.status).toBe(401);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run test/api/locations.test.ts`
Expected: FAIL — route missing.

- [ ] **Step 3: Implement**

`src/lib/schemas/locations.ts`:
```ts
import { z } from 'zod';
export const locationPoint = z.object({
  lat: z.number().min(-90).max(90),
  lng: z.number().min(-180).max(180),
  recorded_at: z.string().datetime(),
  speed: z.number().nonnegative().optional(),
  accuracy: z.number().nonnegative().optional(),
  battery_level: z.number().int().min(0).max(100).optional(),
});
export const locationBatch = z.object({
  points: z.array(locationPoint).min(1).max(Number(process.env.MAX_LOCATION_BATCH ?? 200)),
});
```

`src/app/api/locations/route.ts`:
```ts
import { sql } from 'drizzle-orm';
import { eq } from 'drizzle-orm';
import { db } from '@/db/client';
import { devices } from '@/db/schema';
import { requireDevice } from '@/lib/auth/device';
import { parseBody } from '@/lib/validate';
import { ok } from '@/lib/http';
import { ensureLocationPartition } from '@/db/partitions';
import { fireLowBatteryIfNeeded } from '@/lib/alerts/engine';
import { locationBatch } from '@/lib/schemas/locations';

export const runtime = 'nodejs';

export async function POST(req: Request) {
  const a = await requireDevice(req); if ('response' in a) return a.response;
  const p = await parseBody(req, locationBatch); if ('response' in p) return p.response;
  const deviceId = a.device.id;

  // ensure partitions for all referenced months
  const months = new Set(p.data.points.map((pt) => pt.recorded_at.slice(0, 7)));
  for (const ym of months) await ensureLocationPartition(new Date(`${ym}-01T00:00:00Z`));

  let inserted = 0;
  for (const pt of p.data.points) {
    const r = await db.execute(sql`
      INSERT INTO locations (device_id, geom, speed, accuracy, battery_level, recorded_at)
      VALUES (${deviceId}, ST_SetSRID(ST_MakePoint(${pt.lng}, ${pt.lat}), 4326),
              ${pt.speed ?? null}, ${pt.accuracy ?? null}, ${pt.battery_level ?? null}, ${pt.recorded_at})
      ON CONFLICT (device_id, recorded_at) DO NOTHING`);
    inserted += r.rowCount ?? 0;
  }

  // denormalize latest point
  const latest = p.data.points.reduce((a, b) => (a.recorded_at >= b.recorded_at ? a : b));
  await db.execute(sql`
    UPDATE devices SET
      last_location = ST_SetSRID(ST_MakePoint(${latest.lng}, ${latest.lat}), 4326),
      last_location_at = ${latest.recorded_at},
      last_seen_at = now(),
      battery_level = COALESCE(${latest.battery_level ?? null}, battery_level)
    WHERE id = ${deviceId}`);

  const [fresh] = await db.select().from(devices).where(eq(devices.id, deviceId));
  await fireLowBatteryIfNeeded(fresh);
  return ok({ inserted });
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run test/api/locations.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add FamilyShield/src/app/api/locations FamilyShield/src/lib/schemas/locations.ts FamilyShield/test/api/locations.test.ts
git commit -m "feat(familyshield): batch location ingestion with idempotency + denorm"
```

---

### Task 10: Device status heartbeat (`POST /api/device/status`)

**Files:**
- Create: `src/app/api/device/status/route.ts`, `src/lib/schemas/status.ts`
- Test: `test/api/status.test.ts`

**Interfaces:**
- Consumes: `requireDevice`, `parseBody`, `fireLowBatteryIfNeeded`.
- Produces: response `{ ok: true }`. Updates `batteryLevel`, `isCharging`, `fcmToken`, `lastSeenAt`.

- [ ] **Step 1: Write the failing test**

`test/api/status.test.ts`:
```ts
import { describe, it, expect, beforeAll, beforeEach } from 'vitest';
import { resetDb } from '../helpers/db';
import { seedParent, seedChild, seedDevice } from '../helpers/factories';
import { setSender } from '@/lib/alerts/fcm';
import { db } from '@/db/client';
import { devices } from '@/db/schema';
import { eq } from 'drizzle-orm';
import { POST as status } from '@/app/api/device/status/route';

beforeAll(async () => { await resetDb(); });
beforeEach(() => setSender({ async send() { return true; } }));

describe('device status', () => {
  it('updates battery, charging, fcm token, last seen', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    const { token, device } = await seedDevice(c.id);
    const r = await status(new Request('http://t/', {
      method: 'POST', headers: { authorization: `Bearer ${token}` },
      body: JSON.stringify({ battery_level: 55, is_charging: true, fcm_token: 'fcm-abc' }),
    }));
    expect(r.status).toBe(200);
    const [d] = await db.select().from(devices).where(eq(devices.id, device.id));
    expect(d.batteryLevel).toBe(55);
    expect(d.fcmToken).toBe('fcm-abc');
    expect(d.lastSeenAt).toBeTruthy();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run test/api/status.test.ts`
Expected: FAIL — route missing.

- [ ] **Step 3: Implement**

`src/lib/schemas/status.ts`:
```ts
import { z } from 'zod';
export const statusSchema = z.object({
  battery_level: z.number().int().min(0).max(100).optional(),
  is_charging: z.boolean().optional(),
  fcm_token: z.string().min(1).optional(),
});
```

`src/app/api/device/status/route.ts`:
```ts
import { eq } from 'drizzle-orm';
import { db } from '@/db/client';
import { devices } from '@/db/schema';
import { requireDevice } from '@/lib/auth/device';
import { parseBody } from '@/lib/validate';
import { ok } from '@/lib/http';
import { fireLowBatteryIfNeeded } from '@/lib/alerts/engine';
import { statusSchema } from '@/lib/schemas/status';

export const runtime = 'nodejs';

export async function POST(req: Request) {
  const a = await requireDevice(req); if ('response' in a) return a.response;
  const p = await parseBody(req, statusSchema); if ('response' in p) return p.response;
  await db.update(devices).set({
    batteryLevel: p.data.battery_level ?? a.device.batteryLevel,
    isCharging: p.data.is_charging ?? a.device.isCharging,
    fcmToken: p.data.fcm_token ?? a.device.fcmToken,
    lastSeenAt: new Date(),
  }).where(eq(devices.id, a.device.id));
  const [fresh] = await db.select().from(devices).where(eq(devices.id, a.device.id));
  await fireLowBatteryIfNeeded(fresh);
  return ok({ ok: true });
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run test/api/status.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add FamilyShield/src/app/api/device FamilyShield/src/lib/schemas/status.ts FamilyShield/test/api/status.test.ts
git commit -m "feat(familyshield): device status heartbeat"
```

---

### Task 11: Current location, history, parent push-token

**Files:**
- Create: `src/app/api/children/[id]/location/current/route.ts`, `src/app/api/children/[id]/location/history/route.ts`, `src/app/api/parent/push-token/route.ts`, `src/lib/schemas/parent.ts`
- Test: `test/api/location-read.test.ts`

**Interfaces:**
- Consumes: `requireParent`, `assertChildOwned`, `decodeCursor`/`encodeCursor`, raw SQL `ST_X`/`ST_Y`.
- Produces:
  - current → `{ lat, lng, recordedAt } | null`.
  - history → `{ points: [{ lat, lng, recordedAt, speed, accuracy }], nextCursor: string | null }`.
  - push-token → `{ ok: true }`, sets `parents.fcmToken`.

- [ ] **Step 1: Write the failing test**

`test/api/location-read.test.ts`:
```ts
import { describe, it, expect, beforeAll, beforeEach } from 'vitest';
import { resetDb } from '../helpers/db';
import { seedParent, seedChild, seedDevice } from '../helpers/factories';
import { setSender } from '@/lib/alerts/fcm';
import { signAccess } from '@/lib/auth/jwt';
import { POST as upload } from '@/app/api/locations/route';
import { GET as current } from '@/app/api/children/[id]/location/current/route';
import { GET as history } from '@/app/api/children/[id]/location/history/route';

beforeAll(async () => { await resetDb(); });
beforeEach(() => setSender({ async send() { return true; } }));

describe('location reads', () => {
  it('returns current and history for an owned child', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    const { token } = await seedDevice(c.id);
    await upload(new Request('http://t/', {
      method: 'POST', headers: { authorization: `Bearer ${token}` },
      body: JSON.stringify({ points: [
        { lat: 32.07, lng: 34.78, recorded_at: '2026-06-19T08:00:00Z' },
        { lat: 32.08, lng: 34.79, recorded_at: '2026-06-19T08:05:00Z' },
      ] }),
    }));
    const ptok = await signAccess(p.id);
    const ctx = { params: Promise.resolve({ id: c.id }) };

    const cur = await current(new Request('http://t/', { headers: { authorization: `Bearer ${ptok}` } }), ctx);
    const curBody = await cur.json();
    expect(curBody.lng).toBeCloseTo(34.79, 5);

    const his = await history(new Request('http://t/?date=2026-06-19', { headers: { authorization: `Bearer ${ptok}` } }), ctx);
    expect((await his.json()).points).toHaveLength(2);
  });

  it('forbids reading a child you do not own', async () => {
    const p1 = await seedParent(); const p2 = await seedParent();
    const c = await seedChild(p1.id);
    const ptok = await signAccess(p2.id);
    const r = await current(new Request('http://t/', { headers: { authorization: `Bearer ${ptok}` } }),
      { params: Promise.resolve({ id: c.id }) });
    expect(r.status).toBe(404);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run test/api/location-read.test.ts`
Expected: FAIL — routes missing.

- [ ] **Step 3: Implement**

`src/lib/schemas/parent.ts`:
```ts
import { z } from 'zod';
export const pushTokenSchema = z.object({ fcm_token: z.string().min(1) });
export const historyQuery = z.object({
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  cursor: z.string().optional(),
  limit: z.coerce.number().int().min(1).max(500).default(200),
});
```

`src/app/api/children/[id]/location/current/route.ts`:
```ts
import { sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { requireParent } from '@/lib/auth/parent';
import { assertChildOwned } from '@/lib/ownership';
import { ok, err } from '@/lib/http';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string }> };

export async function GET(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const r = await db.execute(sql`
    SELECT ST_Y(d.last_location) AS lat, ST_X(d.last_location) AS lng, d.last_location_at AS recorded_at
    FROM devices d WHERE d.child_id = ${id} AND d.last_location IS NOT NULL
    ORDER BY d.last_location_at DESC LIMIT 1`);
  const row = r.rows[0] as any;
  if (!row) return ok(null);
  return ok({ lat: row.lat, lng: row.lng, recordedAt: new Date(row.recorded_at).toISOString() });
}
```

`src/app/api/children/[id]/location/history/route.ts`:
```ts
import { sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { requireParent } from '@/lib/auth/parent';
import { assertChildOwned } from '@/lib/ownership';
import { parseQuery } from '@/lib/validate';
import { ok, err } from '@/lib/http';
import { decodeCursor, encodeCursor } from '@/lib/pagination';
import { historyQuery } from '@/lib/schemas/parent';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string }> };

export async function GET(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const q = parseQuery(new URL(req.url), historyQuery); if ('response' in q) return q.response;

  const dayStart = `${q.data.date}T00:00:00Z`;
  const dayEnd = `${q.data.date}T23:59:59.999Z`;
  const cur = q.data.cursor ? decodeCursor(q.data.cursor) : null;
  const limit = q.data.limit;

  const r = await db.execute(sql`
    SELECT l.id, ST_Y(l.geom) AS lat, ST_X(l.geom) AS lng, l.recorded_at, l.speed, l.accuracy
    FROM locations l
    JOIN devices d ON d.id = l.device_id
    WHERE d.child_id = ${id}
      AND l.recorded_at >= ${dayStart} AND l.recorded_at <= ${dayEnd}
      ${cur ? sql`AND (l.recorded_at, l.id) > (${cur.recordedAt}, ${cur.id})` : sql``}
    ORDER BY l.recorded_at ASC, l.id ASC
    LIMIT ${limit + 1}`);

  const rows = r.rows as any[];
  const page = rows.slice(0, limit);
  const next = rows.length > limit
    ? encodeCursor({ recordedAt: new Date(page[page.length - 1].recorded_at).toISOString(), id: page[page.length - 1].id })
    : null;
  return ok({
    points: page.map((x) => ({
      lat: x.lat, lng: x.lng, recordedAt: new Date(x.recorded_at).toISOString(),
      speed: x.speed, accuracy: x.accuracy,
    })),
    nextCursor: next,
  });
}
```

`src/app/api/parent/push-token/route.ts`:
```ts
import { eq } from 'drizzle-orm';
import { db } from '@/db/client';
import { parents } from '@/db/schema';
import { requireParent } from '@/lib/auth/parent';
import { parseBody } from '@/lib/validate';
import { ok } from '@/lib/http';
import { pushTokenSchema } from '@/lib/schemas/parent';

export const runtime = 'nodejs';

export async function POST(req: Request) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const p = await parseBody(req, pushTokenSchema); if ('response' in p) return p.response;
  await db.update(parents).set({ fcmToken: p.data.fcm_token }).where(eq(parents.id, a.parentId));
  return ok({ ok: true });
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run test/api/location-read.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add FamilyShield/src/app/api/children/[id]/location FamilyShield/src/app/api/parent FamilyShield/src/lib/schemas/parent.ts FamilyShield/test/api/location-read.test.ts
git commit -m "feat(familyshield): current location, history pagination, parent push-token"
```

---

### Task 12: Alerts listing + device revoke

**Files:**
- Create: `src/app/api/children/[id]/alerts/route.ts`, `src/app/api/devices/[id]/route.ts`
- Test: `test/api/alerts.test.ts`

**Interfaces:**
- Consumes: `requireParent`, `assertChildOwned`, cursor helpers, `db`.
- Produces:
  - alerts list → `{ alerts: [...], nextCursor: string | null }` (keyset on `createdAt`,`id`).
  - `DELETE /api/devices/:id` → `{ ok: true }`, sets `revokedAt` (only for owned devices).

- [ ] **Step 1: Write the failing test**

`test/api/alerts.test.ts`:
```ts
import { describe, it, expect, beforeAll } from 'vitest';
import { resetDb } from '../helpers/db';
import { seedParent, seedChild, seedDevice } from '../helpers/factories';
import { db } from '@/db/client';
import { alerts, devices } from '@/db/schema';
import { eq } from 'drizzle-orm';
import { signAccess } from '@/lib/auth/jwt';
import { GET as listAlerts } from '@/app/api/children/[id]/alerts/route';
import { DELETE as revoke } from '@/app/api/devices/[id]/route';

beforeAll(async () => { await resetDb(); });

describe('alerts + revoke', () => {
  it('lists alerts for an owned child', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    await db.insert(alerts).values({ childId: c.id, type: 'offline', payload: {} });
    const ptok = await signAccess(p.id);
    const r = await listAlerts(new Request('http://t/', { headers: { authorization: `Bearer ${ptok}` } }),
      { params: Promise.resolve({ id: c.id }) });
    expect((await r.json()).alerts).toHaveLength(1);
  });

  it('revokes an owned device', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    const { device } = await seedDevice(c.id);
    const ptok = await signAccess(p.id);
    const r = await revoke(new Request('http://t/', { method: 'DELETE', headers: { authorization: `Bearer ${ptok}` } }),
      { params: Promise.resolve({ id: device.id }) });
    expect(r.status).toBe(200);
    const [d] = await db.select().from(devices).where(eq(devices.id, device.id));
    expect(d.revokedAt).toBeTruthy();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run test/api/alerts.test.ts`
Expected: FAIL — routes missing.

- [ ] **Step 3: Implement**

`src/app/api/children/[id]/alerts/route.ts`:
```ts
import { and, eq, lt, or, desc } from 'drizzle-orm';
import { db } from '@/db/client';
import { alerts } from '@/db/schema';
import { requireParent } from '@/lib/auth/parent';
import { assertChildOwned } from '@/lib/ownership';
import { ok, err } from '@/lib/http';
import { decodeCursor, encodeCursor } from '@/lib/pagination';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string }> };

export async function GET(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const url = new URL(req.url);
  const limit = Math.min(Number(url.searchParams.get('limit') ?? 50), 200);
  const cur = url.searchParams.get('cursor') ? decodeCursor(url.searchParams.get('cursor')!) : null;

  const rows = await db.select().from(alerts).where(and(
    eq(alerts.childId, id),
    cur ? or(
      lt(alerts.createdAt, new Date(cur.recordedAt)),
      and(eq(alerts.createdAt, new Date(cur.recordedAt)), lt(alerts.id, cur.id)),
    ) : undefined,
  )).orderBy(desc(alerts.createdAt), desc(alerts.id)).limit(limit + 1);

  const page = rows.slice(0, limit);
  const last = page[page.length - 1];
  const next = rows.length > limit && last
    ? encodeCursor({ recordedAt: last.createdAt.toISOString(), id: last.id }) : null;
  return ok({ alerts: page, nextCursor: next });
}
```

`src/app/api/devices/[id]/route.ts`:
```ts
import { and, eq } from 'drizzle-orm';
import { db } from '@/db/client';
import { devices, children } from '@/db/schema';
import { requireParent } from '@/lib/auth/parent';
import { ok, err } from '@/lib/http';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string }> };

export async function DELETE(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  const [owned] = await db.select({ id: devices.id })
    .from(devices).innerJoin(children, eq(devices.childId, children.id))
    .where(and(eq(devices.id, id), eq(children.parentId, a.parentId)));
  if (!owned) return err('not_found', 'Device not found', 404);
  await db.update(devices).set({ revokedAt: new Date() }).where(eq(devices.id, id));
  return ok({ ok: true });
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run test/api/alerts.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add FamilyShield/src/app/api/children/[id]/alerts FamilyShield/src/app/api/devices FamilyShield/test/api/alerts.test.ts
git commit -m "feat(familyshield): alerts listing + device revoke"
```

---

### Task 13: Safe-zone CRUD (storage only)

**Files:**
- Create: `src/app/api/children/[id]/zones/route.ts`, `src/app/api/children/[id]/zones/[zoneId]/route.ts`, `src/lib/schemas/zones.ts`
- Test: `test/api/zones.test.ts`

**Interfaces:**
- Consumes: `requireParent`, `assertChildOwned`, raw SQL for `center` geometry.
- Produces: zone create `{ name, lat, lng, radiusM, notifyOnEnter?, notifyOnExit?, dwellMinutes? }` → row; list; patch; delete. NO firing logic.

- [ ] **Step 1: Write the failing test**

`test/api/zones.test.ts`:
```ts
import { describe, it, expect, beforeAll } from 'vitest';
import { resetDb } from '../helpers/db';
import { seedParent, seedChild } from '../helpers/factories';
import { signAccess } from '@/lib/auth/jwt';
import { POST as createZone, GET as listZones } from '@/app/api/children/[id]/zones/route';
import { DELETE as delZone } from '@/app/api/children/[id]/zones/[zoneId]/route';

beforeAll(async () => { await resetDb(); });

describe('safe zones', () => {
  it('creates, lists, deletes a zone', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    const ptok = await signAccess(p.id);
    const ctx = { params: Promise.resolve({ id: c.id }) };
    const r1 = await createZone(new Request('http://t/', {
      method: 'POST', headers: { authorization: `Bearer ${ptok}` },
      body: JSON.stringify({ name: 'Home', lat: 32.07, lng: 34.78, radiusM: 150 }),
    }), ctx);
    expect(r1.status).toBe(201);
    const zone = await r1.json();

    const r2 = await listZones(new Request('http://t/', { headers: { authorization: `Bearer ${ptok}` } }), ctx);
    expect((await r2.json()).zones).toHaveLength(1);

    const r3 = await delZone(new Request('http://t/', { method: 'DELETE', headers: { authorization: `Bearer ${ptok}` } }),
      { params: Promise.resolve({ id: c.id, zoneId: zone.id }) });
    expect(r3.status).toBe(200);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run test/api/zones.test.ts`
Expected: FAIL — routes missing.

- [ ] **Step 3: Implement**

`src/lib/schemas/zones.ts`:
```ts
import { z } from 'zod';
export const createZoneSchema = z.object({
  name: z.string().min(1).max(80),
  lat: z.number().min(-90).max(90),
  lng: z.number().min(-180).max(180),
  radiusM: z.number().int().min(10).max(50000),
  notifyOnEnter: z.boolean().optional(),
  notifyOnExit: z.boolean().optional(),
  dwellMinutes: z.number().int().min(1).max(1440).optional(),
});
```

`src/app/api/children/[id]/zones/route.ts`:
```ts
import { sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { requireParent } from '@/lib/auth/parent';
import { assertChildOwned } from '@/lib/ownership';
import { parseBody } from '@/lib/validate';
import { ok, err } from '@/lib/http';
import { createZoneSchema } from '@/lib/schemas/zones';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string }> };

export async function POST(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const p = await parseBody(req, createZoneSchema); if ('response' in p) return p.response;
  const r = await db.execute(sql`
    INSERT INTO safe_zones (child_id, name, center, radius_m, notify_on_enter, notify_on_exit, dwell_minutes)
    VALUES (${id}, ${p.data.name}, ST_SetSRID(ST_MakePoint(${p.data.lng}, ${p.data.lat}), 4326),
            ${p.data.radiusM}, ${p.data.notifyOnEnter ?? true}, ${p.data.notifyOnExit ?? true},
            ${p.data.dwellMinutes ?? null})
    RETURNING id, name, radius_m, notify_on_enter, notify_on_exit, dwell_minutes`);
  return ok(r.rows[0], 201);
}

export async function GET(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const r = await db.execute(sql`
    SELECT id, name, ST_Y(center) AS lat, ST_X(center) AS lng, radius_m, notify_on_enter, notify_on_exit, dwell_minutes
    FROM safe_zones WHERE child_id = ${id} ORDER BY created_at DESC`);
  return ok({ zones: r.rows });
}
```

`src/app/api/children/[id]/zones/[zoneId]/route.ts`:
```ts
import { and, eq } from 'drizzle-orm';
import { db } from '@/db/client';
import { safeZones } from '@/db/schema';
import { requireParent } from '@/lib/auth/parent';
import { assertChildOwned } from '@/lib/ownership';
import { ok, err } from '@/lib/http';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string; zoneId: string }> };

export async function DELETE(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id, zoneId } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const deleted = await db.delete(safeZones)
    .where(and(eq(safeZones.id, zoneId), eq(safeZones.childId, id))).returning();
  if (!deleted.length) return err('not_found', 'Zone not found', 404);
  return ok({ ok: true });
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run test/api/zones.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add FamilyShield/src/app/api/children/[id]/zones FamilyShield/src/lib/schemas/zones.ts FamilyShield/test/api/zones.test.ts
git commit -m "feat(familyshield): safe-zone CRUD storage"
```

---

### Task 14: Offline-sweep cron + offline alert

**Files:**
- Create: `src/app/api/cron/offline-sweep/route.ts`; Modify: `src/lib/alerts/engine.ts`; Create: `vercel.json`
- Test: `test/api/offline.test.ts`

**Interfaces:**
- Consumes: `getSender`, `db`, `OFFLINE_THRESHOLD_MIN`, `CRON_SECRET`.
- Produces: `fireOfflineSweep(): Promise<{ fired: number }>` in `engine.ts`; cron route guarded by `Authorization: Bearer $CRON_SECRET`. Fires one `offline` alert per stale device with no open offline alert.

- [ ] **Step 1: Write the failing test**

`test/api/offline.test.ts`:
```ts
import { describe, it, expect, beforeAll, beforeEach } from 'vitest';
import { resetDb } from '../helpers/db';
import { seedParent, seedChild, seedDevice } from '../helpers/factories';
import { db } from '@/db/client';
import { devices, alerts } from '@/db/schema';
import { eq } from 'drizzle-orm';
import { setSender } from '@/lib/alerts/fcm';
import { fireOfflineSweep } from '@/lib/alerts/engine';

beforeAll(async () => { await resetDb(); });
beforeEach(() => setSender({ async send() { return true; } }));

describe('offline sweep', () => {
  it('fires once for a stale device and not again', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    const { device } = await seedDevice(c.id);
    await db.update(devices)
      .set({ lastSeenAt: new Date(Date.now() - 60 * 60_000) })
      .where(eq(devices.id, device.id));
    const r1 = await fireOfflineSweep();
    expect(r1.fired).toBe(1);
    const r2 = await fireOfflineSweep();
    expect(r2.fired).toBe(0);
    const rows = await db.select().from(alerts).where(eq(alerts.childId, c.id));
    expect(rows).toHaveLength(1);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run test/api/offline.test.ts`
Expected: FAIL — `fireOfflineSweep` missing.

- [ ] **Step 3: Implement**

Append to `src/lib/alerts/engine.ts`:
```ts
import { sql, isNull, and as and2 } from 'drizzle-orm';

export async function fireOfflineSweep(): Promise<{ fired: number }> {
  const thresholdMin = Number(process.env.OFFLINE_THRESHOLD_MIN ?? 30);
  const cutoff = new Date(Date.now() - thresholdMin * 60_000);

  // stale, non-revoked devices with no unread offline alert
  const stale = await db.execute(sql`
    SELECT d.id AS device_id, d.child_id
    FROM devices d
    WHERE d.revoked_at IS NULL
      AND d.last_seen_at IS NOT NULL
      AND d.last_seen_at < ${cutoff.toISOString()}
      AND NOT EXISTS (
        SELECT 1 FROM alerts a
        WHERE a.device_id = d.id AND a.type = 'offline' AND a.read_at IS NULL
      )`);

  let fired = 0;
  for (const row of stale.rows as any[]) {
    const [a] = await db.insert(alerts).values({
      childId: row.child_id, deviceId: row.device_id, type: 'offline', payload: {},
    }).returning();
    const fcm = await parentFcmFor(row.child_id);
    if (fcm && await getSender().send(fcm, 'Device offline', 'Child device is offline', { type: 'offline' })) {
      await db.update(alerts).set({ deliveredAt: new Date() }).where(eq(alerts.id, a.id));
    }
    fired++;
  }
  return { fired };
}
```
> Reuse the existing `parentFcmFor`, `db`, `alerts`, `getSender`, `eq` imports already in `engine.ts`; only add `sql` to the imports if not present.

`src/app/api/cron/offline-sweep/route.ts`:
```ts
import { fireOfflineSweep } from '@/lib/alerts/engine';
import { ok, err } from '@/lib/http';

export const runtime = 'nodejs';

export async function GET(req: Request) {
  const auth = req.headers.get('authorization') ?? '';
  if (auth !== `Bearer ${process.env.CRON_SECRET}`) return err('unauthorized', 'Bad cron secret', 401);
  return ok(await fireOfflineSweep());
}
```

`vercel.json`:
```json
{ "crons": [{ "path": "/api/cron/offline-sweep", "schedule": "*/5 * * * *" }] }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run test/api/offline.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add FamilyShield/src/app/api/cron FamilyShield/src/lib/alerts/engine.ts FamilyShield/vercel.json FamilyShield/test/api/offline.test.ts
git commit -m "feat(familyshield): offline-sweep cron + offline alerts"
```

---

### Task 15: Rate limiting on auth + pairing endpoints

**Files:**
- Create: `src/lib/ratelimit.ts`; Modify: `src/app/api/auth/login/route.ts`, `src/app/api/auth/register/route.ts`, `src/app/api/pair/route.ts`
- Test: `test/lib/ratelimit.test.ts`

**Interfaces:**
- Produces: `RateLimiter` interface + `memoryLimiter(max, windowMs): RateLimiter` with `check(key): { allowed: boolean }`; `clientKey(req, suffix): string`. `tooMany()` helper returning a 429 `Response`.

- [ ] **Step 1: Write the failing test**

`test/lib/ratelimit.test.ts`:
```ts
import { describe, it, expect } from 'vitest';
import { memoryLimiter, clientKey } from '@/lib/ratelimit';

describe('rate limiter', () => {
  it('allows up to max then blocks within window', () => {
    const rl = memoryLimiter(2, 1000);
    expect(rl.check('k').allowed).toBe(true);
    expect(rl.check('k').allowed).toBe(true);
    expect(rl.check('k').allowed).toBe(false);
    expect(rl.check('other').allowed).toBe(true);
  });
  it('derives a key from headers', () => {
    const req = new Request('http://t/', { headers: { 'x-forwarded-for': '1.2.3.4' } });
    expect(clientKey(req, 'login')).toBe('login:1.2.3.4');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run test/lib/ratelimit.test.ts`
Expected: FAIL — module missing.

- [ ] **Step 3: Implement + wire in**

`src/lib/ratelimit.ts`:
```ts
import { err } from './http';

export interface RateLimiter { check(key: string): { allowed: boolean }; }

export function memoryLimiter(max: number, windowMs: number): RateLimiter {
  const hits = new Map<string, { count: number; reset: number }>();
  return {
    check(key) {
      const now = Date.now();
      const e = hits.get(key);
      if (!e || e.reset < now) { hits.set(key, { count: 1, reset: now + windowMs }); return { allowed: true }; }
      e.count++;
      return { allowed: e.count <= max };
    },
  };
}

export function clientKey(req: Request, suffix: string): string {
  const ip = (req.headers.get('x-forwarded-for') ?? 'unknown').split(',')[0]!.trim();
  return `${suffix}:${ip}`;
}

export const tooMany = () => err('rate_limited', 'Too many requests, slow down', 429);
```

> Production note (in code comment): in-memory limiter is per-instance; for multi-instance scale swap `memoryLimiter` for an Upstash/Redis-backed implementation behind the same `RateLimiter` interface. The interface boundary keeps this a drop-in change.

Wire into `login/route.ts` (and identically `register` with `auth_register`, `pair` with `pair`), adding at the top of `POST` before parsing:
```ts
import { memoryLimiter, clientKey, tooMany } from '@/lib/ratelimit';
const loginLimiter = memoryLimiter(10, 60_000);
// inside POST, first line:
if (!loginLimiter.check(clientKey(req, 'auth_login')).allowed) return tooMany();
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run test/lib/ratelimit.test.ts && npx vitest run test/api/auth.test.ts`
Expected: PASS (auth tests still green — limit is 10/min, above test volume).

- [ ] **Step 5: Commit**

```bash
git add FamilyShield/src/lib/ratelimit.ts FamilyShield/src/app/api/auth FamilyShield/src/app/api/pair FamilyShield/test/lib/ratelimit.test.ts
git commit -m "feat(familyshield): rate limiting on auth and pairing"
```

---

### Task 16: OpenAPI document + Swagger UI

**Files:**
- Create: `src/lib/openapi/registry.ts`, `src/lib/openapi/document.ts`, `src/app/api/openapi.json/route.ts`, `src/app/api/docs/route.ts`
- Test: `test/api/openapi.test.ts`

**Interfaces:**
- Consumes: all exported Zod schemas (`registerSchema`, `loginSchema`, `pairSchema`, `locationBatch`, `statusSchema`, `createChildSchema`, `createZoneSchema`, `pushTokenSchema`, `historyQuery`, `tokenPairSchema`).
- Produces: `buildOpenApiDocument(): OpenAPIObject` covering every endpoint + both bearer schemes; `/api/openapi.json` serves it; `/api/docs` serves Swagger UI HTML pointing at it.

- [ ] **Step 1: Write the failing test**

`test/api/openapi.test.ts`:
```ts
import { describe, it, expect } from 'vitest';
import { GET as openapi } from '@/app/api/openapi.json/route';
import { GET as docs } from '@/app/api/docs/route';

describe('openapi', () => {
  it('serves a valid-ish spec covering core paths', async () => {
    const doc = await (await openapi(new Request('http://t/'))).json();
    expect(doc.openapi).toMatch(/^3\./);
    expect(doc.paths['/api/auth/register']).toBeDefined();
    expect(doc.paths['/api/pair']).toBeDefined();
    expect(doc.paths['/api/locations']).toBeDefined();
    expect(doc.components.securitySchemes.parentJwt).toBeDefined();
    expect(doc.components.securitySchemes.deviceToken).toBeDefined();
  });
  it('serves swagger html', async () => {
    const r = await docs(new Request('http://t/'));
    expect(r.headers.get('content-type')).toContain('text/html');
    expect(await r.text()).toContain('swagger-ui');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npx vitest run test/api/openapi.test.ts`
Expected: FAIL — routes missing.

- [ ] **Step 3: Implement**

`src/lib/openapi/registry.ts`:
```ts
import { OpenAPIRegistry, extendZodWithOpenApi } from '@asteasolutions/zod-to-openapi';
import { z } from 'zod';
extendZodWithOpenApi(z);
export const registry = new OpenAPIRegistry();
export { z };
```

`src/lib/openapi/document.ts`:
```ts
import { OpenApiGeneratorV31 } from '@asteasolutions/zod-to-openapi';
import { registry } from './registry';
import { registerSchema, loginSchema, refreshSchema, tokenPairSchema } from '@/lib/schemas/auth';
import { pairSchema } from '@/lib/schemas/pair';
import { locationBatch } from '@/lib/schemas/locations';
import { statusSchema } from '@/lib/schemas/status';
import { createChildSchema } from '@/lib/schemas/children';
import { createZoneSchema } from '@/lib/schemas/zones';
import { pushTokenSchema, historyQuery } from '@/lib/schemas/parent';

let built: object | null = null;

export function buildOpenApiDocument() {
  if (built) return built;

  registry.registerComponent('securitySchemes', 'parentJwt', { type: 'http', scheme: 'bearer', bearerFormat: 'JWT' });
  registry.registerComponent('securitySchemes', 'deviceToken', { type: 'http', scheme: 'bearer' });

  const json = (schema: unknown) => ({ content: { 'application/json': { schema: schema as any } } });

  registry.registerPath({ method: 'post', path: '/api/auth/register', request: { body: json(registerSchema) },
    responses: { 201: { description: 'Created', ...json(tokenPairSchema) }, 409: { description: 'Email taken' } } });
  registry.registerPath({ method: 'post', path: '/api/auth/login', request: { body: json(loginSchema) },
    responses: { 200: { description: 'OK', ...json(tokenPairSchema) }, 401: { description: 'Bad credentials' } } });
  registry.registerPath({ method: 'post', path: '/api/auth/refresh', request: { body: json(refreshSchema) },
    responses: { 200: { description: 'OK', ...json(tokenPairSchema) } } });
  registry.registerPath({ method: 'post', path: '/api/pair', request: { body: json(pairSchema) },
    responses: { 201: { description: 'Paired' }, 400: { description: 'Invalid code' } } });
  registry.registerPath({ method: 'post', path: '/api/locations', security: [{ deviceToken: [] }],
    request: { body: json(locationBatch) }, responses: { 200: { description: 'Ingested' }, 401: { description: 'Unauthorized' } } });
  registry.registerPath({ method: 'post', path: '/api/device/status', security: [{ deviceToken: [] }],
    request: { body: json(statusSchema) }, responses: { 200: { description: 'OK' } } });
  registry.registerPath({ method: 'post', path: '/api/children', security: [{ parentJwt: [] }],
    request: { body: json(createChildSchema) }, responses: { 201: { description: 'Created' } } });
  registry.registerPath({ method: 'get', path: '/api/children', security: [{ parentJwt: [] }],
    responses: { 200: { description: 'List' } } });
  registry.registerPath({ method: 'post', path: '/api/children/{id}/pairing-code', security: [{ parentJwt: [] }],
    responses: { 201: { description: 'Code' } } });
  registry.registerPath({ method: 'get', path: '/api/children/{id}/location/current', security: [{ parentJwt: [] }],
    responses: { 200: { description: 'Current location' } } });
  registry.registerPath({ method: 'get', path: '/api/children/{id}/location/history', security: [{ parentJwt: [] }],
    request: { query: historyQuery }, responses: { 200: { description: 'History' } } });
  registry.registerPath({ method: 'get', path: '/api/children/{id}/alerts', security: [{ parentJwt: [] }],
    responses: { 200: { description: 'Alerts' } } });
  registry.registerPath({ method: 'post', path: '/api/children/{id}/zones', security: [{ parentJwt: [] }],
    request: { body: json(createZoneSchema) }, responses: { 201: { description: 'Zone' } } });
  registry.registerPath({ method: 'post', path: '/api/parent/push-token', security: [{ parentJwt: [] }],
    request: { body: json(pushTokenSchema) }, responses: { 200: { description: 'OK' } } });

  const generator = new OpenApiGeneratorV31(registry.definitions);
  built = generator.generateDocument({
    openapi: '3.1.0',
    info: { title: 'FamilyShield API', version: '1.0.0' },
  });
  return built;
}
```

`src/app/api/openapi.json/route.ts`:
```ts
import { buildOpenApiDocument } from '@/lib/openapi/document';
export const runtime = 'nodejs';
export async function GET() { return Response.json(buildOpenApiDocument()); }
```

`src/app/api/docs/route.ts`:
```ts
export const runtime = 'nodejs';
export async function GET() {
  const html = `<!doctype html><html><head><meta charset="utf-8"/>
<title>FamilyShield API</title>
<link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css"/></head>
<body><div id="swagger-ui"></div>
<script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
<script>window.ui = SwaggerUIBundle({ url: '/api/openapi.json', dom_id: '#swagger-ui' });</script>
</body></html>`;
  return new Response(html, { headers: { 'content-type': 'text/html; charset=utf-8' } });
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npx vitest run test/api/openapi.test.ts`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add FamilyShield/src/lib/openapi FamilyShield/src/app/api/openapi.json FamilyShield/src/app/api/docs FamilyShield/test/api/openapi.test.ts
git commit -m "feat(familyshield): OpenAPI document + Swagger UI"
```

---

### Task 17: Full-suite green + README

**Files:**
- Create: `FamilyShield/README.md`
- Test: entire `test/` suite

**Interfaces:** none new.

- [ ] **Step 1: Run the full suite**

Run: `cd FamilyShield && npx vitest run`
Expected: ALL tests pass.

- [ ] **Step 2: Type-check**

Run: `cd FamilyShield && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 3: Write README**

`README.md` documenting: env setup (`.env` from `.env.example`, a Postgres+PostGIS test DB), `npm run db:generate && npm run db:migrate`, applying `drizzle/0001_locations_partition.sql`, `npm test`, `npm run dev`, and the `/api/docs` Swagger URL. Include the endpoint table from spec §6.

- [ ] **Step 4: Verify dev server boots**

Run: `cd FamilyShield && npm run build`
Expected: build succeeds (all routes compile).

- [ ] **Step 5: Commit**

```bash
git add FamilyShield/README.md
git commit -m "docs(familyshield): backend README + final suite green"
```

---

## Self-Review

**Spec coverage check (spec §6/§8/§9/§10 → tasks):**
- Parent auth → Task 5. Children CRUD + pairing code → Task 6. Pairing → Task 7. Location ingestion (idempotent, denorm) → Task 9. Device status → Task 10. Current/history (cursor) → Task 11. Parent push-token → Task 11. Alerts list + revoke → Task 12. Safe-zone CRUD → Task 13. Low-battery alert → Task 8. Offline alert + cron → Task 14. Rate limiting → Task 15. Swagger/OpenAPI → Task 16. Partitioning/denorm/cursor/idempotency (scale) → Tasks 2, 9, 11, 12. Encryption (TLS + at-rest, no app crypto on coords) → satisfied structurally (no coord encryption anywhere); transport handled by Vercel. Full test coverage → every task is TDD; Task 17 runs the whole suite.

**Placeholder scan:** No "TBD/TODO" in code steps; each code step contains complete code. Two ordering notes (factories needing `hashToken`; `parents.fcmToken` introduced early in Task 8) are explicit instructions, not placeholders.

**Type consistency:** Guard return shape `{ parentId } | { response }` and `{ device } | { response }` used consistently. `assertChildOwned` returns row-or-null, always checked. Cursor shape `{ recordedAt, id }` consistent across history (Task 11) and alerts (Task 12). `PushSender.send` signature consistent across `fcm.ts`, `engine.ts`, and all tests. Schema export names referenced in Task 16 match their definitions in Tasks 5/6/7/9/10/11/13.

**Known cross-task ordering dependencies (call-outs for the executor):**
1. Implement `hashToken`/`createDeviceToken` (Task 4's device.ts crypto) before Task 2's factories run — Task 2 notes this.
2. Add `parents.fcmToken` column during Task 8 (engine needs it); Task 11 only adds the endpoint. Regenerate `drizzle/0000_init.sql` after any schema edit and re-run `resetDb`.
3. After Task 1, run `npm run db:generate` to produce `drizzle/0000_init.sql` referenced by the test harness.
