import { and, eq, gt, sql } from 'drizzle-orm';
import { db } from '../../db/client';
import { devices, childParentLinks, parents, alerts, safeZoneStates } from '../../db/schema';
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

async function sendToParent(parentId: string, title: string, body: string, data: Record<string, string>) {
  const [parent] = await db.select({ token: parents.fcmToken }).from(parents).where(eq(parents.id, parentId));
  return parent?.token ? getSender().send(parent.token, title, body, data) : false;
}

type ZoneTransitionInput = {
  device: Device;
  lat: number;
  lng: number;
  recordedAt: string;
};

type ZoneProbeRow = {
  id: string;
  parent_id: string;
  name: string;
  radius_m: number;
  notify_on_enter: boolean;
  notify_on_exit: boolean;
  is_inside: boolean;
};

async function fireSafeZoneAlert(
  device: Device,
  row: ZoneProbeRow,
  type: 'safe_zone_enter' | 'safe_zone_exit',
  lat: number,
  lng: number,
) {
  const verb = type === 'safe_zone_enter' ? 'entered' : 'left';
  const [a] = await db.insert(alerts).values({
    childId: device.childId,
    parentId: row.parent_id,
    deviceId: device.id,
    type,
    payload: { zoneId: row.id, zoneName: row.name, radiusM: row.radius_m, lat, lng },
  }).returning();

  if (await sendToParent(row.parent_id, `Safe zone ${verb}`, `Child ${verb} ${row.name}`, {
    type,
    childId: device.childId,
    zoneId: row.id,
    zoneName: row.name,
  })) {
    await db.update(alerts).set({ deliveredAt: new Date() }).where(eq(alerts.id, a.id));
  }
}

export async function fireSafeZoneTransitions({ device, lat, lng, recordedAt }: ZoneTransitionInput): Promise<void> {
  const probe = await db.execute(sql`
    SELECT z.id, z.parent_id, z.name, z.radius_m, z.notify_on_enter, z.notify_on_exit,
      ST_DWithin(
        z.center::geography,
        ST_SetSRID(ST_MakePoint(${lng}, ${lat}), 4326)::geography,
        z.radius_m
      ) AS is_inside
    FROM safe_zones z
    INNER JOIN child_parent_links l ON l.parent_id = z.parent_id AND l.child_id = ${device.childId}
    WHERE z.active = true`);

  const transitionAt = new Date(recordedAt);
  for (const row of probe.rows as ZoneProbeRow[]) {
    const [state] = await db.select().from(safeZoneStates).where(and(
      eq(safeZoneStates.parentId, row.parent_id),
      eq(safeZoneStates.childId, device.childId),
      eq(safeZoneStates.zoneId, row.id),
    ));

    if (!state) {
      await db.insert(safeZoneStates).values({
        parentId: row.parent_id,
        childId: device.childId,
        zoneId: row.id,
        isInside: row.is_inside,
        lastTransitionAt: row.is_inside ? transitionAt : null,
      });
      if (row.is_inside && row.notify_on_enter) {
        await fireSafeZoneAlert(device, row, 'safe_zone_enter', lat, lng);
      }
      continue;
    }

    if (state.isInside === row.is_inside) {
      await db.update(safeZoneStates).set({ updatedAt: new Date() }).where(eq(safeZoneStates.id, state.id));
      continue;
    }

    await db.update(safeZoneStates).set({
      isInside: row.is_inside,
      lastTransitionAt: transitionAt,
      updatedAt: new Date(),
    }).where(eq(safeZoneStates.id, state.id));

    if (row.is_inside && row.notify_on_enter) {
      await fireSafeZoneAlert(device, row, 'safe_zone_enter', lat, lng);
    } else if (!row.is_inside && row.notify_on_exit) {
      await fireSafeZoneAlert(device, row, 'safe_zone_exit', lat, lng);
    }
  }
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

export async function fireChildUnpaired(device: Device): Promise<void> {
  const [a] = await db.insert(alerts).values({
    childId: device.childId,
    deviceId: device.id,
    type: 'child_unpaired',
    payload: {},
  }).returning();
  if (await sendToParents(device.childId, 'Child device unpaired', 'The child deliberately unpaired this device', { type: 'child_unpaired' })) {
    await db.update(alerts).set({ deliveredAt: new Date() }).where(eq(alerts.id, a.id));
  }
}

export async function fireParentRemovedByChild(parentId: string, device: Device): Promise<void> {
  await sendToParent(parentId, 'Child device unpaired', 'The child removed this parent from the device', {
    type: 'child_unpaired',
    childId: device.childId,
  });
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
