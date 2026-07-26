import { describe, it, expect, beforeAll, beforeEach } from 'vitest';
import { resetDb } from '../helpers/db';
import { seedParent, seedChild, seedDevice } from '../helpers/factories';
import { db } from '@/db/client';
import { devices, alerts } from '@/db/schema';
import { eq } from 'drizzle-orm';
import { fireLowBatteryIfNeeded } from '@/lib/alerts/engine';
import { setSender, resetSender, type PushOptions } from '@/lib/alerts/fcm';

let sent = 0;
let pushes: Array<{ data?: Record<string, string>; options?: PushOptions }> = [];
beforeAll(async () => { await resetDb(); });
beforeEach(() => {
  sent = 0;
  pushes = [];
  setSender({
    async send(_token, _title, _body, data, options) {
      sent++;
      pushes.push({ data, options });
      return true;
    },
  });
});

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
    expect(pushes[0].data).toEqual(expect.objectContaining({ type: 'low_battery', childId: c.id, batteryLevel: '10' }));
    expect(pushes[0].options?.includeNotification).toBe(false);
    resetSender();
  });
});
