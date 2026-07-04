import { describe, it, expect, beforeAll } from 'vitest';
import { eq, sql } from 'drizzle-orm';
import { resetDb } from '../helpers/db';
import { seedChild, seedDevice, seedParent } from '../helpers/factories';
import { db } from '@/db/client';
import { childParentLinks, devices, locations, parents, subscriptionTiers } from '@/db/schema';
import { signAccess } from '@/lib/auth/jwt';
import { POST as upload } from '@/app/api/locations/route';
import { GET as current } from '@/app/api/children/[id]/location/current/route';
import { GET as history } from '@/app/api/children/[id]/location/history/route';
import { sweepLocationRetention } from '@/lib/retention';

beforeAll(async () => { await resetDb(); });

const post = (token: string, body: unknown) => new Request('http://t/', {
  method: 'POST',
  headers: { authorization: `Bearer ${token}` },
  body: JSON.stringify(body),
});
const get = (token: string, query = '') => new Request('http://t/' + query, {
  headers: { authorization: `Bearer ${token}` },
});
const ctx = (id: string) => ({ params: Promise.resolve({ id }) });
const daysAgo = (days: number) => new Date(Date.now() - days * 24 * 60 * 60 * 1000);

describe('tier-based location retention', () => {
  it('seeds free tier and backfills parents to free', async () => {
    const [free] = await db.select().from(subscriptionTiers).where(eq(subscriptionTiers.code, 'free'));
    expect(free.locationRetentionDays).toBe(2);
    expect(free.maxChildren).toBe(5);

    const p = await seedParent('tier_seed@test.io');
    expect(p.tierCode).toBe('free');
  });

  it('filters expired current/history reads before cron cleanup', async () => {
    const p = await seedParent('retention_read@test.io'); const c = await seedChild(p.id);
    const { token } = await seedDevice(c.id);
    const old = daysAgo(3);
    await upload(post(token, { points: [{ lat: 1, lng: 2, recorded_at: old.toISOString() }] }));
    const parentToken = await signAccess(p.id);

    expect(await (await current(get(parentToken), ctx(c.id))).json()).toBeNull();
    const hist = await history(get(parentToken, `?date=${old.toISOString().slice(0, 10)}`), ctx(c.id));
    expect((await hist.json()).points).toHaveLength(0);
  });

  it('deletes expired rows and clears stale denormalized current location', async () => {
    const p = await seedParent('retention_sweep@test.io'); const c = await seedChild(p.id);
    const { token, device } = await seedDevice(c.id);
    const old = daysAgo(3);
    const fresh = daysAgo(1);
    await upload(post(token, { points: [
      { lat: 1, lng: 2, recorded_at: old.toISOString() },
      { lat: 3, lng: 4, recorded_at: fresh.toISOString() },
    ] }));

    const result = await sweepLocationRetention();
    expect(result.deleted).toBeGreaterThanOrEqual(1);
    const rows = await db.select().from(locations).where(eq(locations.deviceId, device.id));
    expect(rows).toHaveLength(1);

    await db.update(devices).set({ lastLocationAt: old }).where(eq(devices.id, device.id));
    const cleared = await sweepLocationRetention();
    expect(cleared.clearedDevices).toBeGreaterThanOrEqual(1);
    const [d] = await db.select().from(devices).where(eq(devices.id, device.id));
    expect(d.lastLocationAt).toBeNull();
  });

  it('uses max tier retention across linked parents while storing locations once', async () => {
    const p1 = await seedParent('max_retention_p1@test.io');
    const p2 = await seedParent('max_retention_p2@test.io');
    await db.insert(subscriptionTiers).values({
      code: 'pro_test',
      name: 'Pro Test',
      locationRetentionDays: 30,
      maxChildren: 20,
    }).onConflictDoUpdate({
      target: subscriptionTiers.code,
      set: { locationRetentionDays: 30, maxChildren: 20, isActive: true },
    });
    await db.update(parents).set({ tierCode: 'pro_test' }).where(eq(parents.id, p2.id));
    const c = await seedChild(p1.id, 'Shared');
    await db.insert(childParentLinks).values({ childId: c.id, parentId: p2.id, displayName: 'Shared', role: 'caregiver' });
    const { token, device } = await seedDevice(c.id);
    const oldForFree = daysAgo(3);
    await upload(post(token, { points: [{ lat: 5, lng: 6, recorded_at: oldForFree.toISOString() }] }));

    const parentToken = await signAccess(p1.id);
    const hist = await history(get(parentToken, `?date=${oldForFree.toISOString().slice(0, 10)}`), ctx(c.id));
    expect((await hist.json()).points).toHaveLength(1);
    const rowCount = await db.execute(sql`SELECT count(*)::int AS count FROM locations WHERE device_id = ${device.id}`);
    expect((rowCount.rows[0] as any).count).toBe(1);
  });
});
