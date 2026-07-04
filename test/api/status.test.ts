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
