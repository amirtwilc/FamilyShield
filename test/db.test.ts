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
    const now = new Date();
    await ensureLocationPartition(now);
    await ensureLocationPartition(new Date(now.getTime() + 60_000)); // same month, no error
    const partition = `locations_${now.getUTCFullYear()}_${String(now.getUTCMonth() + 1).padStart(2, '0')}`;
    const r = await db.execute(
      sql`SELECT to_regclass(${`public.${partition}`}) AS t`);
    expect((r.rows[0] as any).t).toBe(partition);
  });

  it('seeds a parent', async () => {
    const p = await seedParent();
    expect(p.id).toBeTruthy();
  });
});
