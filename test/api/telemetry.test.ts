import { describe, it, expect, beforeAll, beforeEach } from 'vitest';
import { eq } from 'drizzle-orm';
import { POST as telemetry } from '@/app/api/device/telemetry/route';
import { db } from '@/db/client';
import { devices, locations } from '@/db/schema';
import { setSender } from '@/lib/alerts/fcm';
import { resetDb } from '../helpers/db';
import { seedChild, seedDevice, seedParent } from '../helpers/factories';

beforeAll(async () => { await resetDb(); });
beforeEach(() => setSender({ async send() { return true; } }));

const post = (token: string, body: unknown) => new Request('http://t/', {
  method: 'POST',
  headers: { authorization: `Bearer ${token}` },
  body: JSON.stringify(body),
});

describe('device telemetry', () => {
  it('ingests batched movement samples and denormalizes the latest location', async () => {
    const parent = await seedParent();
    const child = await seedChild(parent.id);
    const { token, device } = await seedDevice(child.id);

    const points = [
      { lat: 32.070, lng: 34.780, recorded_at: '2026-07-27T17:00:00Z' },
      { lat: 32.071, lng: 34.781, recorded_at: '2026-07-27T17:01:00Z' },
      { lat: 32.072, lng: 34.782, recorded_at: '2026-07-27T17:02:00Z', battery_level: 82 },
    ];
    const response = await telemetry(post(token, {
      status: { battery_level: 82, is_charging: false },
      location: points[2],
      locations: points,
    }));

    expect(await response.json()).toMatchObject({ ok: true, locationInserted: 3 });

    const rows = await db.select().from(locations).where(eq(locations.deviceId, device.id));
    expect(rows).toHaveLength(3);
    const [freshDevice] = await db.select().from(devices).where(eq(devices.id, device.id));
    expect(freshDevice.lastLocationAt?.toISOString()).toBe('2026-07-27T17:02:00.000Z');
    expect(freshDevice.batteryLevel).toBe(82);
  });
});
