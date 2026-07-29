import { beforeAll, describe, expect, it } from 'vitest';
import { eq, sql } from 'drizzle-orm';
import { GET as routes } from '@/app/api/children/[id]/routes/route';
import { db } from '@/db/client';
import { ensureLocationPartition } from '@/db/partitions';
import { parents, subscriptionTiers } from '@/db/schema';
import { signAccess } from '@/lib/auth/jwt';
import { resetDb } from '../helpers/db';
import { seedChild, seedDevice, seedParent } from '../helpers/factories';

beforeAll(async () => { await resetDb(); });

describe('route analysis API', () => {
  it('keeps the newest 5000 samples when the analysis window is larger', async () => {
    const parent = await seedParent('routes-latest@test.io');
    await db.insert(subscriptionTiers).values({
      code: 'routes_test',
      name: 'Routes Test',
      locationRetentionDays: 30,
      maxChildren: 10,
    });
    await db.update(parents).set({ tierCode: 'routes_test' }).where(eq(parents.id, parent.id));
    const child = await seedChild(parent.id);
    const { device } = await seedDevice(child.id);
    const start = new Date(Date.now() - 5009 * 60_000);
    await ensureLocationPartition(start);
    await ensureLocationPartition(new Date());

    await db.execute(sql`
      INSERT INTO locations (device_id, geom, recorded_at)
      SELECT
        ${device.id},
        ST_SetSRID(ST_MakePoint(
          CASE WHEN sample < 5000 THEN 6.94 ELSE 6.98 END,
          CASE WHEN sample < 5000 THEN 50.93 ELSE 50.95 END
        ), 4326),
        ${start.toISOString()}::timestamptz + sample * interval '1 minute'
      FROM generate_series(0, 5009) AS sample`);

    const response = await routes(new Request('http://t/?days=14', {
      headers: { authorization: `Bearer ${await signAccess(parent.id)}` },
    }), { params: Promise.resolve({ id: child.id }) });
    const body = await response.json();

    expect(response.status).toBe(200);
    expect(body.stops).toHaveLength(2);
    expect(body.trips).toHaveLength(1);
    expect(body.frequentLocations).toEqual([]);
    expect(body.trips[0].to.lat).toBeCloseTo(50.95);
    expect(body.trips[0].to.lng).toBeCloseTo(6.98);
  });
});
