import { and, eq, gt, sql } from 'drizzle-orm';
import { db } from '../../db/client';
import {
  devices,
  children,
  childParentLinks,
  parents,
  alerts,
  safeZoneStates,
  appUsageLimits,
  appUsageLimitEvents,
} from '../../db/schema';
import { getSender, type PushOptions } from './fcm';

type Device = typeof devices.$inferSelect;

const LOCALIZED_ALERT_PUSH_OPTIONS: PushOptions = {
  includeNotification: false,
  android: {
    priority: 'high',
    ttlMs: 60 * 60 * 1000,
  },
};

async function parentFcmsFor(childId: string): Promise<string[]> {
  // parent.fcmToken added in Task 11; until then this returns null safely.
  const rows = await db.select({ token: parents.fcmToken })
    .from(childParentLinks).innerJoin(parents, eq(childParentLinks.parentId, parents.id))
    .where(eq(childParentLinks.childId, childId));
  return rows.map((r) => r.token).filter((t): t is string => Boolean(t));
}

async function sendToParents(childId: string, title: string, body: string, data: Record<string, string>, options?: PushOptions) {
  let sent = false;
  for (const fcm of await parentFcmsFor(childId)) {
    sent = await getSender().send(fcm, title, body, data, options) || sent;
  }
  return sent;
}

async function sendToParent(parentId: string, title: string, body: string, data: Record<string, string>, options?: PushOptions) {
  const [parent] = await db.select({ token: parents.fcmToken }).from(parents).where(eq(parents.id, parentId));
  return parent?.token ? getSender().send(parent.token, title, body, data, options) : false;
}

async function childDisplayNameForParent(parentId: string, childId: string): Promise<string> {
  const [row] = await db.select({
    linkName: childParentLinks.displayName,
    childName: children.displayName,
  }).from(children)
    .leftJoin(childParentLinks, and(
      eq(childParentLinks.childId, children.id),
      eq(childParentLinks.parentId, parentId),
    ))
    .where(eq(children.id, childId));
  return row?.linkName ?? row?.childName ?? 'Child';
}

type ZoneTransitionInput = {
  device: Device;
  lat: number;
  lng: number;
  recordedAt: string;
};

type ZoneTransitionPoint = {
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

type ZoneObservation = ZoneProbeRow & ZoneTransitionPoint;

type ZoneTransition = {
  type: 'safe_zone_enter' | 'safe_zone_exit';
  observation: ZoneObservation;
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
  }, LOCALIZED_ALERT_PUSH_OPTIONS)) {
    await db.update(alerts).set({ deliveredAt: new Date() }).where(eq(alerts.id, a.id));
  }
}

async function probeSafeZoneObservations(device: Device, point: ZoneTransitionPoint): Promise<ZoneObservation[]> {
  const probe = await db.execute(sql`
    SELECT z.id, z.parent_id, z.name, z.radius_m, z.notify_on_enter, z.notify_on_exit,
      ST_DWithin(
        z.center::geography,
        ST_SetSRID(ST_MakePoint(${point.lng}, ${point.lat}), 4326)::geography,
        z.radius_m
      ) AS is_inside
    FROM safe_zones z
    INNER JOIN child_parent_links l ON l.parent_id = z.parent_id AND l.child_id = ${device.childId}
    WHERE z.active = true`);

  return (probe.rows as ZoneProbeRow[]).map((row) => ({ ...row, ...point }));
}

export async function fireSafeZoneTransitions({ device, lat, lng, recordedAt }: ZoneTransitionInput): Promise<void> {
  await fireSafeZoneTransitionsForBatch({ device, points: [{ lat, lng, recordedAt }] });
}

export async function fireSafeZoneTransitionsForBatch({
  device,
  points,
}: {
  device: Device;
  points: ZoneTransitionPoint[];
}): Promise<void> {
  const orderedPoints = [...points].sort((left, right) =>
    Date.parse(left.recordedAt) - Date.parse(right.recordedAt));
  if (orderedPoints.length === 0) return;

  const observationsByZone = new Map<string, ZoneObservation[]>();
  for (const point of orderedPoints) {
    for (const observation of await probeSafeZoneObservations(device, point)) {
      const key = `${observation.parent_id}:${observation.id}`;
      const observations = observationsByZone.get(key) ?? [];
      observations.push(observation);
      observationsByZone.set(key, observations);
    }
  }

  const transitions: ZoneTransition[] = [];
  for (const observations of observationsByZone.values()) {
    const first = observations[0]!;
    const transition = await db.transaction(async (tx) => {
      const latest = observations[observations.length - 1]!;
      const latestAt = new Date(latest.recordedAt);
      const inserted = await tx.insert(safeZoneStates).values({
        parentId: first.parent_id,
        childId: device.childId,
        zoneId: first.id,
        isInside: latest.is_inside,
        lastTransitionAt: latest.is_inside ? latestAt : null,
        lastObservedAt: latestAt,
      }).onConflictDoNothing().returning({ id: safeZoneStates.id });
      if (inserted.length > 0) {
        return latest.is_inside && latest.notify_on_enter
          ? { type: 'safe_zone_enter' as const, observation: latest }
          : null;
      }

      const locked = await tx.execute(sql`
        SELECT id, is_inside, last_observed_at
        FROM safe_zone_states
        WHERE parent_id = ${first.parent_id}
          AND child_id = ${device.childId}
          AND zone_id = ${first.id}
        FOR UPDATE`);
      const state = locked.rows[0] as {
        id: string;
        is_inside: boolean;
        last_observed_at: Date | string | null;
      };
      const lastObservedAt = state.last_observed_at ? new Date(state.last_observed_at) : null;
      const newObservations = lastObservedAt
        ? observations.filter((observation) => new Date(observation.recordedAt) > lastObservedAt)
        : observations;
      if (newObservations.length === 0) return null;

      const newLatest = newObservations[newObservations.length - 1]!;
      const newLatestAt = new Date(newLatest.recordedAt);

      if (state.is_inside === newLatest.is_inside) {
        await tx.update(safeZoneStates).set({
          lastObservedAt: newLatestAt,
          updatedAt: new Date(),
        }).where(eq(safeZoneStates.id, state.id));
        return null;
      }

      await tx.update(safeZoneStates).set({
        isInside: newLatest.is_inside,
        lastTransitionAt: newLatestAt,
        lastObservedAt: newLatestAt,
        updatedAt: new Date(),
      }).where(eq(safeZoneStates.id, state.id));
      if (newLatest.is_inside && newLatest.notify_on_enter) {
        return { type: 'safe_zone_enter' as const, observation: newLatest };
      }
      if (!newLatest.is_inside && newLatest.notify_on_exit) {
        return { type: 'safe_zone_exit' as const, observation: newLatest };
      }
      return null;
    });

    if (transition) transitions.push(transition);
  }

  for (const transition of resolveSafeZoneBatchNotifications(transitions)) {
    await fireSafeZoneAlert(
      device,
      transition.observation,
      transition.type,
      transition.observation.lat,
      transition.observation.lng,
    );
  }
}

function resolveSafeZoneBatchNotifications(transitions: ZoneTransition[]): ZoneTransition[] {
  const byParent = new Map<string, ZoneTransition[]>();
  for (const transition of transitions) {
    const parentTransitions = byParent.get(transition.observation.parent_id) ?? [];
    parentTransitions.push(transition);
    byParent.set(transition.observation.parent_id, parentTransitions);
  }

  const resolved: ZoneTransition[] = [];
  for (const parentTransitions of byParent.values()) {
    const hasEnter = parentTransitions.some((transition) => transition.type === 'safe_zone_enter');
    const hasExit = parentTransitions.some((transition) => transition.type === 'safe_zone_exit');
    // A single uploaded batch can include locally sampled points from both sides of
    // a trip. When that creates opposite zone notifications for the same parent,
    // prefer the final entered zone(s) so the parent sees where the child ended up.
    resolved.push(...(hasEnter && hasExit
      ? parentTransitions.filter((transition) => transition.type === 'safe_zone_enter')
      : parentTransitions));
  }
  return resolved;
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

  if (await sendToParents(device.childId, 'Low battery', `Battery at ${device.batteryLevel}%`, {
    type: 'low_battery',
    childId: device.childId,
    batteryLevel: String(device.batteryLevel),
  }, LOCALIZED_ALERT_PUSH_OPTIONS)) {
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
  if (await sendToParents(device.childId, 'Child device unpaired', 'The child deliberately unpaired this device', {
    type: 'child_unpaired',
    childId: device.childId,
  }, LOCALIZED_ALERT_PUSH_OPTIONS)) {
    await db.update(alerts).set({ deliveredAt: new Date() }).where(eq(alerts.id, a.id));
  }
}

export async function fireAppUsageLimitAlertsForDay(childId: string, day: string): Promise<{ fired: number }> {
  const limits = await db.select().from(appUsageLimits).where(and(
    eq(appUsageLimits.childId, childId),
    eq(appUsageLimits.active, true),
  ));
  if (limits.length === 0) return { fired: 0 };

  const totalR = await db.execute(sql`
    SELECT COALESCE(SUM(minutes), 0)::int AS min
    FROM app_usage
    WHERE child_id = ${childId} AND day = ${day}::date AND is_relevant = true AND minutes >= 5`);
  const appR = await db.execute(sql`
    SELECT package_name AS "packageName", app, COALESCE(SUM(minutes), 0)::int AS min
    FROM app_usage
    WHERE child_id = ${childId} AND day = ${day}::date AND is_relevant = true AND minutes >= 5
    GROUP BY package_name, app`);

  const totalMin = (totalR.rows[0] as { min: number }).min;
  const byPackage = new Map<string, number>();
  const byApp = new Map<string, number>();
  for (const row of appR.rows as { packageName: string; app: string; min: number }[]) {
    byPackage.set(row.packageName, (byPackage.get(row.packageName) ?? 0) + row.min);
    byApp.set(row.app, (byApp.get(row.app) ?? 0) + row.min);
  }

  let fired = 0;
  for (const limit of limits) {
    const usageMin = limit.type === 'total'
      ? totalMin
      : limit.packageName
        ? byPackage.get(limit.packageName) ?? 0
        : byApp.get(limit.app ?? '') ?? 0;
    if (usageMin < limit.limitMinutes) continue;

    const inserted = await db.execute(sql`
      INSERT INTO app_usage_limit_events (limit_id, parent_id, child_id, day, usage_minutes, limit_minutes)
      VALUES (${limit.id}, ${limit.parentId}, ${limit.childId}, ${day}::date, ${usageMin}, ${limit.limitMinutes})
      ON CONFLICT (limit_id, day) DO NOTHING
      RETURNING id`);
    const event = (inserted.rows as { id: string }[])[0];
    if (!event) continue;

    const isAppLimit = limit.type === 'app';
    const childName = await childDisplayNameForParent(limit.parentId, limit.childId);
    const title = isAppLimit ? `${limit.app ?? 'App'} usage alert` : 'Daily screen time alert';
    const body = `${childName} used ${usageMin} minutes; alert is set at ${limit.limitMinutes} minutes`;
    const [alert] = await db.insert(alerts).values({
      childId: limit.childId,
      parentId: limit.parentId,
      type: 'app_usage_limit_exceeded',
      payload: {
        limitId: limit.id,
        limitType: limit.type,
        app: limit.app,
        packageName: limit.packageName,
        usageMinutes: usageMin,
        limitMinutes: limit.limitMinutes,
        childName,
        day,
      },
    }).returning();
    await db.update(appUsageLimitEvents).set({ alertId: alert.id }).where(eq(appUsageLimitEvents.id, event.id));

    if (await sendToParent(limit.parentId, title, body, {
      type: 'app_usage_limit_exceeded',
      childId: limit.childId,
      childName,
      limitId: limit.id,
      limitType: limit.type,
      usageMinutes: String(usageMin),
      limitMinutes: String(limit.limitMinutes),
      ...(limit.app ? { app: limit.app } : {}),
      ...(limit.packageName ? { packageName: limit.packageName } : {}),
    }, LOCALIZED_ALERT_PUSH_OPTIONS)) {
      await db.update(alerts).set({ deliveredAt: new Date() }).where(eq(alerts.id, alert.id));
    }
    fired++;
  }
  return { fired };
}

export async function fireParentRemovedByChild(parentId: string, device: Device): Promise<void> {
  await sendToParent(parentId, 'Child device unpaired', 'The child removed this parent from the device', {
    type: 'child_unpaired',
    childId: device.childId,
  }, LOCALIZED_ALERT_PUSH_OPTIONS);
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
    if (await sendToParents(row.child_id, 'Device offline', 'Child device is offline', {
      type: 'offline',
      childId: row.child_id,
    }, LOCALIZED_ALERT_PUSH_OPTIONS)) {
      await db.update(alerts).set({ deliveredAt: new Date() }).where(eq(alerts.id, a.id));
    }
    fired++;
  }
  return { fired };
}
