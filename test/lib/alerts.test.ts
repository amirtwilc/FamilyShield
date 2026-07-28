import { describe, it, expect, beforeAll, beforeEach } from 'vitest';
import { resetDb } from '../helpers/db';
import { seedParent, seedChild, seedDevice } from '../helpers/factories';
import { db } from '@/db/client';
import { devices, alerts, safeZones, safeZoneStates } from '@/db/schema';
import { eq } from 'drizzle-orm';
import { fireLowBatteryIfNeeded, fireSafeZoneTransitionsForBatch } from '@/lib/alerts/engine';
import { setSender, resetSender, type PushOptions } from '@/lib/alerts/fcm';

let sent = 0;
let pushes: Array<{ data?: Record<string, string>; options?: PushOptions }> = [];
beforeAll(async () => { await resetDb(); });
beforeEach(async () => {
  await resetDb();
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

describe('safe-zone alerts', () => {
  it('collapses opposite transitions from one location batch into the final zone state', async () => {
    const p = await seedParent();
    const c = await seedChild(p.id);
    const { device } = await seedDevice(c.id);
    const [zone] = await db.insert(safeZones).values({
      parentId: p.id,
      sourceChildId: c.id,
      name: 'School',
      center: { lat: 32.0, lng: 34.0 },
      radiusM: 500,
    }).returning();

    await fireSafeZoneTransitionsForBatch({
      device,
      points: [
        { lat: 32.01, lng: 34.0, recordedAt: '2026-07-27T17:00:00.000Z' },
        { lat: 32.001, lng: 34.0, recordedAt: '2026-07-27T17:05:00.000Z' },
        { lat: 32.01, lng: 34.0, recordedAt: '2026-07-27T17:10:00.000Z' },
      ],
    });

    expect(await db.select().from(alerts).where(eq(alerts.childId, c.id))).toHaveLength(0);
    expect(sent).toBe(0);

    const [state] = await db.select().from(safeZoneStates).where(eq(safeZoneStates.zoneId, zone.id));
    expect(state.isInside).toBe(false);
    expect(state.lastObservedAt?.toISOString()).toBe('2026-07-27T17:10:00.000Z');
  });

  it('fires only one notification for the net transition in a location batch', async () => {
    const p = await seedParent();
    const c = await seedChild(p.id);
    const { device } = await seedDevice(c.id);
    await db.insert(safeZones).values({
      parentId: p.id,
      sourceChildId: c.id,
      name: 'Home',
      center: { lat: 32.0, lng: 34.0 },
      radiusM: 500,
    });

    await fireSafeZoneTransitionsForBatch({
      device,
      points: [
        { lat: 32.01, lng: 34.0, recordedAt: '2026-07-27T17:00:00.000Z' },
        { lat: 32.001, lng: 34.0, recordedAt: '2026-07-27T17:05:00.000Z' },
      ],
    });

    const rows = await db.select().from(alerts).where(eq(alerts.childId, c.id));
    expect(rows.map((row) => row.type)).toEqual(['safe_zone_enter']);
    expect(sent).toBe(1);
    expect(pushes[0].data).toEqual(expect.objectContaining({ type: 'safe_zone_enter', zoneName: 'Home' }));
  });

  it('does not send enter and exit notifications together when moving between zones', async () => {
    const p = await seedParent();
    const c = await seedChild(p.id);
    const { device } = await seedDevice(c.id);
    await db.insert(safeZones).values([
      {
        parentId: p.id,
        sourceChildId: c.id,
        name: 'Home',
        center: { lat: 32.0, lng: 34.0 },
        radiusM: 500,
      },
      {
        parentId: p.id,
        sourceChildId: c.id,
        name: 'School',
        center: { lat: 32.02, lng: 34.0 },
        radiusM: 500,
      },
    ]);

    await fireSafeZoneTransitionsForBatch({
      device,
      points: [{ lat: 32.0, lng: 34.0, recordedAt: '2026-07-27T17:00:00.000Z' }],
    });
    await db.delete(alerts);
    sent = 0;
    pushes = [];

    await fireSafeZoneTransitionsForBatch({
      device,
      points: [{ lat: 32.02, lng: 34.0, recordedAt: '2026-07-27T17:05:00.000Z' }],
    });

    const rows = await db.select().from(alerts).where(eq(alerts.childId, c.id));
    expect(rows.map((row) => row.type)).toEqual(['safe_zone_enter']);
    expect(rows[0].payload).toEqual(expect.objectContaining({ zoneName: 'School' }));
    expect(sent).toBe(1);
    expect(pushes[0].data).toEqual(expect.objectContaining({ type: 'safe_zone_enter', zoneName: 'School' }));
  });
});
