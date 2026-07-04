import { and, eq, gt, sql } from 'drizzle-orm';
import { db } from '../../db/client';
import { devices, childParentLinks, parents, alerts } from '../../db/schema';
import { getSender } from './fcm';

type Device = typeof devices.$inferSelect;

async function parentFcmsFor(childId: string): Promise<string[]> {
  // parent.fcmToken added in Task 11; until then this returns null safely.
  const rows = await db.select({ token: parents.fcmToken })
    .from(childParentLinks).innerJoin(parents, eq(childParentLinks.parentId, parents.id))
    .where(eq(childParentLinks.childId, childId));
  return rows.map((r) => r.token).filter((t): t is string => Boolean(t));
}

async function sendToParents(childId: string, title: string, body: string, data: Record<string, string>) {
  let sent = false;
  for (const fcm of await parentFcmsFor(childId)) {
    sent = await getSender().send(fcm, title, body, data) || sent;
  }
  return sent;
}

export async function fireLowBatteryIfNeeded(device: Device): Promise<void> {
  const threshold = Number(process.env.LOW_BATTERY_THRESHOLD ?? 15);
  const cooldownMin = Number(process.env.LOW_BATTERY_COOLDOWN_MIN ?? 60);
  if (device.batteryLevel == null || device.batteryLevel > threshold || device.isCharging) return;

  const since = new Date(Date.now() - cooldownMin * 60_000);
  const [recent] = await db.select().from(alerts).where(and(
    eq(alerts.deviceId, device.id), eq(alerts.type, 'low_battery'), gt(alerts.createdAt, since),
  ));
  if (recent) return;

  const [row] = await db.insert(alerts).values({
    childId: device.childId, deviceId: device.id, type: 'low_battery',
    payload: { batteryLevel: device.batteryLevel },
  }).returning();

  if (await sendToParents(device.childId, 'Low battery', `Battery at ${device.batteryLevel}%`, { type: 'low_battery' })) {
    await db.update(alerts).set({ deliveredAt: new Date() }).where(eq(alerts.id, row.id));
  }
}

export async function fireOfflineSweep(): Promise<{ fired: number }> {
  const thresholdMin = Number(process.env.OFFLINE_THRESHOLD_MIN ?? 30);
  const cutoff = new Date(Date.now() - thresholdMin * 60_000);

  // stale, non-revoked devices with no unread offline alert
  const stale = await db.execute(sql`
    SELECT d.id AS device_id, d.child_id
    FROM devices d
    WHERE d.revoked_at IS NULL
      AND d.last_seen_at IS NOT NULL
      AND d.last_seen_at < ${cutoff.toISOString()}
      AND NOT EXISTS (
        SELECT 1 FROM alerts a
        WHERE a.device_id = d.id AND a.type = 'offline' AND a.read_at IS NULL
      )`);

  let fired = 0;
  for (const row of stale.rows as any[]) {
    const [a] = await db.insert(alerts).values({
      childId: row.child_id, deviceId: row.device_id, type: 'offline', payload: {},
    }).returning();
    if (await sendToParents(row.child_id, 'Device offline', 'Child device is offline', { type: 'offline' })) {
      await db.update(alerts).set({ deliveredAt: new Date() }).where(eq(alerts.id, a.id));
    }
    fired++;
  }
  return { fired };
}
