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
