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
