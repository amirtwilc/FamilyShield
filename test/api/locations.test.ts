import { describe, it, expect, beforeAll, beforeEach } from 'vitest';
import { resetDb } from '../helpers/db';
import { seedParent, seedChild, seedDevice } from '../helpers/factories';
import { setSender } from '@/lib/alerts/fcm';
import { db } from '@/db/client';
import { childParentLinks, devices, locations } from '@/db/schema';
import { eq } from 'drizzle-orm';
import { signAccess } from '@/lib/auth/jwt';
import { POST as upload } from '@/app/api/locations/route';
import { POST as createZone } from '@/app/api/children/[id]/zones/route';
import { GET as listAlerts } from '@/app/api/children/[id]/alerts/route';

beforeAll(async () => { await resetDb(); });
beforeEach(() => setSender({ async send() { return true; } }));
const post = (token: string, body: unknown) => new Request('http://t/', {
  method: 'POST', headers: { authorization: `Bearer ${token}` }, body: JSON.stringify(body),
});
const baseTime = Date.now() - 2 * 60 * 60_000;
const recordedAt = (minutes: number) => new Date(baseTime + minutes * 60_000).toISOString();

describe('locations ingestion', () => {
  it('ingests a batch, denormalizes last location, is idempotent', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    const { token, device } = await seedDevice(c.id);
    const points = [
      { lat: 32.07, lng: 34.78, recorded_at: recordedAt(0), battery_level: 90 },
      { lat: 32.08, lng: 34.79, recorded_at: recordedAt(5), battery_level: 88 },
    ];
    const r1 = await upload(post(token, { points }));
    expect((await r1.json()).inserted).toBe(2);

    // idempotent re-upload
    const r2 = await upload(post(token, { points }));
    expect((await r2.json()).inserted).toBe(0);

    const rows = await db.select().from(locations).where(eq(locations.deviceId, device.id));
    expect(rows).toHaveLength(2);
    const [d] = await db.select().from(devices).where(eq(devices.id, device.id));
    expect(d.lastLocationAt?.toISOString()).toBe(recordedAt(5));
  });

  it('rejects unauthenticated', async () => {
    const r = await upload(new Request('http://t/', { method: 'POST', body: '{}' }));
    expect(r.status).toBe(401);
  });

  it('fires parent-scoped safe-zone enter and exit alerts without duplicate replay alerts', async () => {
    const p = await seedParent(); const otherParent = await seedParent();
    const c = await seedChild(p.id);
    await db.insert(childParentLinks).values({
      childId: c.id,
      parentId: otherParent.id,
      displayName: c.displayName,
    });
    const { token, device } = await seedDevice(c.id);
    const ptok = await signAccess(p.id);
    const otherTok = await signAccess(otherParent.id);
    const ctx = { params: Promise.resolve({ id: c.id }) };

    await createZone(new Request('http://t/', {
      method: 'POST', headers: { authorization: `Bearer ${ptok}` },
      body: JSON.stringify({ name: 'School', lat: 32.0, lng: 34.0, radiusM: 500 }),
    }), ctx);

    const at = (min: number) => recordedAt(20 + min);
    await upload(post(token, { points: [{ lat: 32.01, lng: 34.0, recorded_at: at(0) }] }));
    await upload(post(token, { points: [{ lat: 32.001, lng: 34.0, recorded_at: at(5) }] }));
    await upload(post(token, { points: [{ lat: 32.001, lng: 34.0, recorded_at: at(5) }] }));
    await upload(post(token, { points: [{ lat: 32.01, lng: 34.0, recorded_at: at(10) }] }));
    // A delayed point from before the exit must not move state/current location
    // backwards or emit a second enter alert.
    await upload(post(token, { points: [{ lat: 32.001, lng: 34.0, recorded_at: at(7) }] }));

    const parentAlerts = await listAlerts(new Request('http://t/', { headers: { authorization: `Bearer ${ptok}` } }), ctx);
    expect((await parentAlerts.json()).alerts.map((a: any) => a.type)).toEqual(['safe_zone_exit', 'safe_zone_enter']);
    const [freshDevice] = await db.select().from(devices).where(eq(devices.id, device.id));
    expect(freshDevice.lastLocationAt?.toISOString()).toBe(at(10));

    const linkedParentAlerts = await listAlerts(new Request('http://t/', { headers: { authorization: `Bearer ${otherTok}` } }), ctx);
    expect((await linkedParentAlerts.json()).alerts).toHaveLength(0);
  });

  it('does not fire alerts for inactive zones', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    const { token } = await seedDevice(c.id);
    const ptok = await signAccess(p.id);
    const ctx = { params: Promise.resolve({ id: c.id }) };

    await createZone(new Request('http://t/', {
      method: 'POST', headers: { authorization: `Bearer ${ptok}` },
      body: JSON.stringify({ name: 'Paused', lat: 32.0, lng: 34.0, radiusM: 500, active: false }),
    }), ctx);

    await upload(post(token, { points: [{ lat: 32.001, lng: 34.0, recorded_at: recordedAt(40) }] }));

    const parentAlerts = await listAlerts(new Request('http://t/', { headers: { authorization: `Bearer ${ptok}` } }), ctx);
    expect((await parentAlerts.json()).alerts).toHaveLength(0);
  });
});
