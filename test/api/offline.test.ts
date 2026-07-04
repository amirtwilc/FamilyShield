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
