import { and, desc, eq, gt, isNotNull, isNull, sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { ensureLocationPartition } from '@/db/partitions';
import { alerts, childParentLinks, children, devices, messages, parents, sosDailyUsage, sosEventReceipts, sosEvents } from '@/db/schema';
import { decryptMessageRow, encryptMessageBody } from '@/lib/messages/crypto';
import { fireLowBatteryIfNeeded, fireSafeZoneTransitions } from '@/lib/alerts/engine';
import { getSender, type PushOptions } from '@/lib/alerts/fcm';
import { pruneInvalidParentPushToken, sendToParentInstallations } from '@/lib/parent-push';

export const URGENT_PUSH_CHANNEL_ID = 'familyshield_urgent';

const URGENT_PUSH_OPTIONS: PushOptions = {
  includeNotification: false,
  android: {
    priority: 'high',
    ttlMs: 60 * 60 * 1000,
    channelId: URGENT_PUSH_CHANNEL_ID,
    notificationPriority: 'max',
  },
};

type Device = typeof devices.$inferSelect;
type LocationInput = {
  lat: number;
  lng: number;
  recorded_at: string;
  battery_level?: number;
  speed?: number;
  accuracy?: number;
};

type SosEventRow = typeof sosEvents.$inferSelect & {
  lastLat?: number | string | null;
  lastLng?: number | string | null;
};

function dateOnly(value: unknown): string {
  return value instanceof Date ? value.toISOString().slice(0, 10) : String(value);
}

export function sosHighRateLimitSeconds(): number {
  return Number(process.env.SOS_HIGH_RATE_DAILY_SECONDS ?? 3600);
}

export function sosHighRateIntervalSeconds(): number {
  return Number(process.env.SOS_HIGH_RATE_INTERVAL_SECONDS ?? 60);
}

export function urgentAlertCooldownSeconds(): number {
  return Number(process.env.URGENT_ALERT_COOLDOWN_SECONDS ?? 30);
}

async function activeSosEvent(childId: string): Promise<typeof sosEvents.$inferSelect | null> {
  const [event] = await db.select().from(sosEvents)
    .where(and(eq(sosEvents.childId, childId), eq(sosEvents.status, 'active')))
    .orderBy(desc(sosEvents.startedAt))
    .limit(1);
  return event ?? null;
}

async function activeSosEventWithLocation(childId: string): Promise<SosEventRow | null> {
  const r = await db.execute(sql`
    SELECT e.id AS "id",
      e.child_id AS "childId",
      e.device_id AS "deviceId",
      e.status AS "status",
      e.started_at AS "startedAt",
      e.ended_at AS "endedAt",
      e.ended_reason AS "endedReason",
      e.timezone AS "timezone",
      e.local_day AS "localDay",
      e.high_rate_limit_seconds AS "highRateLimitSeconds",
      e.high_rate_interval_seconds AS "highRateIntervalSeconds",
      e.last_location_at AS "lastLocationAt",
      e.last_battery_level AS "lastBatteryLevel",
      ST_Y(e.last_location) AS "lastLat",
      ST_X(e.last_location) AS "lastLng"
    FROM sos_events e
    WHERE e.child_id = ${childId} AND e.status = 'active'
    ORDER BY e.started_at DESC
    LIMIT 1`);
  return (r.rows[0] as SosEventRow | undefined) ?? null;
}

async function parentRowsForChild(childId: string): Promise<Array<{ parentId: string; email: string }>> {
  return db.select({
    parentId: parents.id,
    email: parents.email,
  }).from(childParentLinks)
    .innerJoin(parents, eq(parents.id, childParentLinks.parentId))
    .where(eq(childParentLinks.childId, childId));
}

async function childName(childId: string): Promise<string> {
  const [child] = await db.select({ name: children.displayName }).from(children).where(eq(children.id, childId));
  return child?.name ?? 'Child';
}

async function childDeviceTokens(childId: string): Promise<string[]> {
  const tokens = await db.select({ token: devices.fcmToken }).from(devices)
    .where(and(eq(devices.childId, childId), isNull(devices.revokedAt), isNotNull(devices.fcmToken)));
  return tokens.map((row) => row.token).filter((token): token is string => Boolean(token));
}

function pushError(error: unknown): Record<string, string | undefined> {
  const maybe = error as { code?: unknown; message?: unknown };
  return {
    code: typeof maybe.code === 'string' ? maybe.code : undefined,
    message: error instanceof Error ? error.message : String(error),
  };
}

async function sendSafely(
  token: string,
  title: string,
  body: string,
  data: Record<string, string>,
  options?: PushOptions,
): Promise<boolean> {
  try {
    const sent = await getSender().send(token, title, body, data, options);
    if (!sent) console.warn('[push] Urgent/SOS notification was not sent', { type: data.type, childId: data.childId, eventId: data.eventId });
    return sent;
  } catch (error) {
    await pruneInvalidParentPushToken(token, error);
    console.error('[push] Urgent/SOS notification send failed', {
      type: data.type,
      childId: data.childId,
      eventId: data.eventId,
      error: pushError(error),
    });
    return false;
  }
}

async function insertLocation(device: Device, location: LocationInput): Promise<void> {
  await ensureLocationPartition(new Date(`${location.recorded_at.slice(0, 7)}-01T00:00:00Z`));
  const r = await db.execute(sql`
    INSERT INTO locations (device_id, geom, speed, accuracy, battery_level, recorded_at)
    VALUES (${device.id}, ST_SetSRID(ST_MakePoint(${location.lng}, ${location.lat}), 4326),
            ${location.speed ?? null}, ${location.accuracy ?? null}, ${location.battery_level ?? null}, ${location.recorded_at})
    ON CONFLICT (device_id, recorded_at) DO NOTHING`);

  if ((r.rowCount ?? 0) > 0) {
    await fireSafeZoneTransitions({ device, lat: location.lat, lng: location.lng, recordedAt: location.recorded_at });
  }

  await db.execute(sql`
    UPDATE devices SET
      last_location = CASE
        WHEN last_location_at IS NULL OR last_location_at < ${location.recorded_at}
          THEN ST_SetSRID(ST_MakePoint(${location.lng}, ${location.lat}), 4326)
        ELSE last_location
      END,
      last_location_at = GREATEST(last_location_at, ${location.recorded_at}),
      last_seen_at = now(),
      battery_level = CASE
        WHEN last_location_at IS NULL OR last_location_at < ${location.recorded_at}
          THEN COALESCE(${location.battery_level ?? null}, battery_level)
        ELSE battery_level
      END
    WHERE id = ${device.id}`);

  const [fresh] = await db.select().from(devices).where(eq(devices.id, device.id));
  if (fresh) await fireLowBatteryIfNeeded(fresh);
}

async function notifySosStarted(device: Device, eventId: string): Promise<void> {
  const name = await childName(device.childId);
  for (const parent of await parentRowsForChild(device.childId)) {
    const [alert] = await db.insert(alerts).values({
      childId: device.childId,
      parentId: parent.parentId,
      deviceId: device.id,
      type: 'kid_sos_started',
      payload: { eventId },
    }).returning();
    if (await sendToParentInstallations(parent.parentId, (token) =>
      sendSafely(token, `SOS from ${name}`, `${name} needs help`, {
        type: 'kid_sos_started',
        recipient: 'parent',
        childId: device.childId,
        parentId: parent.parentId,
        eventId,
        childName: name,
        priority: 'urgent',
      }, URGENT_PUSH_OPTIONS))) {
      await db.update(alerts).set({ deliveredAt: new Date() }).where(eq(alerts.id, alert.id));
    }
  }
}

async function notifySosEnded(device: Device, eventId: string): Promise<void> {
  const name = await childName(device.childId);
  for (const parent of await parentRowsForChild(device.childId)) {
    const [alert] = await db.insert(alerts).values({
      childId: device.childId,
      parentId: parent.parentId,
      deviceId: device.id,
      type: 'kid_sos_ended',
      payload: { eventId },
    }).returning();
    if (await sendToParentInstallations(parent.parentId, (token) =>
      sendSafely(token, `${name} ended SOS`, `${name} marked the SOS as ended`, {
        type: 'kid_sos_ended',
        recipient: 'parent',
        childId: device.childId,
        parentId: parent.parentId,
        eventId,
        childName: name,
      }, URGENT_PUSH_OPTIONS))) {
      await db.update(alerts).set({ deliveredAt: new Date() }).where(eq(alerts.id, alert.id));
    }
  }
}

async function notifyChildSosAcknowledged(childId: string, parentId: string, eventId: string): Promise<boolean> {
  const [parent] = await db.select({
    email: parents.email,
    parentDisplayName: childParentLinks.parentDisplayName,
  }).from(parents)
    .leftJoin(childParentLinks, and(eq(childParentLinks.childId, childId), eq(childParentLinks.parentId, parents.id)))
    .where(eq(parents.id, parentId));
  const parentName = parent?.parentDisplayName?.trim() || parent?.email || 'A parent';
  let delivered = false;
  for (const token of await childDeviceTokens(childId)) {
    delivered = await sendSafely(token, 'Parent is responding', `${parentName} acknowledged your SOS`, {
      type: 'sos_acknowledged',
      recipient: 'child',
      childId,
      parentId,
      eventId,
      parentName,
      priority: 'urgent',
    }, URGENT_PUSH_OPTIONS) || delivered;
  }
  return delivered;
}

async function chargeHighRateUsage(
  childId: string,
  localDay: string,
  timezone: string,
  limitSeconds: number,
  intervalSeconds: number,
): Promise<{ accepted: boolean; chargedSeconds: number; usedSeconds: number; remainingSeconds: number }> {
  return db.transaction(async (tx) => {
    await tx.insert(sosDailyUsage)
      .values({ childId, day: localDay, timezone, usedSeconds: 0 })
      .onConflictDoNothing();

    const locked = await tx.execute(sql`
      SELECT used_seconds
      FROM sos_daily_usage
      WHERE child_id = ${childId} AND day = ${localDay}::date
      FOR UPDATE`);
    const used = Number((locked.rows[0] as { used_seconds: number }).used_seconds);
    if (used >= limitSeconds) {
      return { accepted: false, chargedSeconds: 0, usedSeconds: used, remainingSeconds: 0 };
    }

    const chargedSeconds = Math.min(intervalSeconds, limitSeconds - used);
    const nextUsed = used + chargedSeconds;
    await tx.update(sosDailyUsage)
      .set({ usedSeconds: nextUsed, timezone, updatedAt: new Date() })
      .where(and(eq(sosDailyUsage.childId, childId), eq(sosDailyUsage.day, localDay)));
    return {
      accepted: true,
      chargedSeconds,
      usedSeconds: nextUsed,
      remainingSeconds: Math.max(0, limitSeconds - nextUsed),
    };
  });
}

async function usageFor(childId: string, localDay: string | null | undefined): Promise<number> {
  if (!localDay) return 0;
  const [usage] = await db.select().from(sosDailyUsage)
    .where(and(eq(sosDailyUsage.childId, childId), eq(sosDailyUsage.day, localDay)));
  return usage?.usedSeconds ?? 0;
}

function eventPayload(event: SosEventRow | null, usedSeconds: number, limitSeconds: number, intervalSeconds: number) {
  if (!event) {
    return {
      active: false,
      event: null,
      dailyUsedSeconds: usedSeconds,
      dailyLimitSeconds: limitSeconds,
      highRateIntervalSeconds: intervalSeconds,
      remainingSeconds: Math.max(0, limitSeconds - usedSeconds),
    };
  }
  const lastLat = event.lastLat == null ? null : Number(event.lastLat);
  const lastLng = event.lastLng == null ? null : Number(event.lastLng);
  return {
    active: true,
    event: {
      id: event.id,
      childId: event.childId,
      status: event.status,
      startedAt: new Date(event.startedAt).toISOString(),
      endedAt: event.endedAt ? new Date(event.endedAt).toISOString() : null,
      timezone: event.timezone,
      localDay: dateOnly(event.localDay),
      lastLocation: lastLat == null || lastLng == null ? null : { lat: lastLat, lng: lastLng },
      lastLocationAt: event.lastLocationAt ? new Date(event.lastLocationAt).toISOString() : null,
      lastBatteryLevel: event.lastBatteryLevel,
    },
    dailyUsedSeconds: usedSeconds,
    dailyLimitSeconds: limitSeconds,
    highRateIntervalSeconds: intervalSeconds,
    remainingSeconds: Math.max(0, limitSeconds - usedSeconds),
  };
}

export async function sosStateForChild(childId: string, localDay?: string | null) {
  const event = await activeSosEventWithLocation(childId);
  const day = localDay ?? (event?.localDay ? dateOnly(event.localDay) : null);
  const limit = event?.highRateLimitSeconds ?? sosHighRateLimitSeconds();
  const interval = event?.highRateIntervalSeconds ?? sosHighRateIntervalSeconds();
  return eventPayload(event, await usageFor(childId, day), limit, interval);
}

export async function startSos(device: Device, input: { timezone?: string; local_day: string; location?: LocationInput }) {
  const timezone = input.timezone ?? 'UTC';
  const existing = await activeSosEvent(device.childId);
  if (existing) return sosStateForChild(device.childId, input.local_day);

  const [event] = await db.insert(sosEvents).values({
    childId: device.childId,
    deviceId: device.id,
    timezone,
    localDay: input.local_day,
    highRateLimitSeconds: sosHighRateLimitSeconds(),
    highRateIntervalSeconds: sosHighRateIntervalSeconds(),
  }).onConflictDoNothing().returning();
  if (!event) return sosStateForChild(device.childId, input.local_day);

  if (input.location) {
    await insertLocation(device, input.location);
    await db.execute(sql`
      UPDATE sos_events SET
        last_location = ST_SetSRID(ST_MakePoint(${input.location.lng}, ${input.location.lat}), 4326),
        last_location_at = ${input.location.recorded_at},
        last_battery_level = ${input.location.battery_level ?? null}
      WHERE id = ${event.id}
        AND (last_location_at IS NULL OR last_location_at < ${input.location.recorded_at})`);
  } else {
    await db.update(devices).set({ lastSeenAt: new Date() }).where(eq(devices.id, device.id));
  }

  await notifySosStarted(device, event.id);
  return sosStateForChild(device.childId, input.local_day);
}

export async function recordSosLocation(device: Device, input: { timezone?: string; local_day: string; location: LocationInput }) {
  const timezone = input.timezone ?? 'UTC';
  const event = await activeSosEvent(device.childId);
  if (!event) return { active: false, accepted: false, reason: 'no_active_sos', state: await sosStateForChild(device.childId, input.local_day) };

  const usage = await chargeHighRateUsage(
    device.childId,
    input.local_day,
    timezone,
    event.highRateLimitSeconds,
    event.highRateIntervalSeconds,
  );
  if (!usage.accepted) {
    return { active: true, accepted: false, reason: 'quota_exhausted', state: await sosStateForChild(device.childId, input.local_day) };
  }

  await insertLocation(device, input.location);
  await db.execute(sql`
    UPDATE sos_events SET
      timezone = ${timezone},
      local_day = ${input.local_day},
      last_location = ST_SetSRID(ST_MakePoint(${input.location.lng}, ${input.location.lat}), 4326),
      last_location_at = ${input.location.recorded_at},
      last_battery_level = ${input.location.battery_level ?? null}
    WHERE id = ${event.id}
      AND (last_location_at IS NULL OR last_location_at < ${input.location.recorded_at})`);
  return { active: true, accepted: true, chargedSeconds: usage.chargedSeconds, state: await sosStateForChild(device.childId, input.local_day) };
}

export async function endSos(device: Device, reason: string) {
  const event = await activeSosEvent(device.childId);
  if (!event) return sosStateForChild(device.childId);
  await db.update(sosEvents).set({
    status: 'ended',
    endedAt: new Date(),
    endedReason: reason,
  }).where(eq(sosEvents.id, event.id));
  await db.update(devices).set({ lastSeenAt: new Date() }).where(eq(devices.id, device.id));
  await notifySosEnded(device, event.id);
  return sosStateForChild(device.childId, dateOnly(event.localDay));
}

export async function acknowledgeSos(childId: string, parentId: string, eventId: string) {
  const [event] = await db.select().from(sosEvents)
    .where(and(eq(sosEvents.id, eventId), eq(sosEvents.childId, childId), eq(sosEvents.status, 'active')));
  if (!event) return null;
  await db.insert(sosEventReceipts).values({
    sosEventId: eventId,
    parentId,
    acknowledgedAt: new Date(),
  }).onConflictDoUpdate({
    target: [sosEventReceipts.sosEventId, sosEventReceipts.parentId],
    set: { acknowledgedAt: new Date() },
  });
  const delivered = await notifyChildSosAcknowledged(childId, parentId, eventId);
  return { ok: true, delivered };
}

export async function sendUrgentAlertToChild(childId: string, parentId: string, body: string) {
  const cooldown = urgentAlertCooldownSeconds();
  const since = new Date(Date.now() - cooldown * 1000);
  const [recent] = await db.select().from(messages).where(and(
    eq(messages.childId, childId),
    eq(messages.parentId, parentId),
    eq(messages.sender, 'parent'),
    eq(messages.priority, 'urgent'),
    gt(messages.createdAt, since),
  )).limit(1);
  if (recent) return { cooldown: true as const, retryAfterSeconds: cooldown };

  const encryptedBody = encryptMessageBody(body);
  const r = await db.execute(sql`
    INSERT INTO messages (child_id, parent_id, sender, body, priority)
    VALUES (${childId}, ${parentId}, 'parent', ${encryptedBody}, 'urgent')
    RETURNING id, sender, body, priority, created_at, read_at`);
  const message = r.rows[0] as { id: string };

  const [alert] = await db.insert(alerts).values({
    childId,
    parentId,
    type: 'urgent_alert',
    payload: { messageId: message.id },
  }).returning();

  let delivered = false;
  for (const token of await childDeviceTokens(childId)) {
    delivered = await sendSafely(token, 'Urgent alert from parent', body, {
      type: 'urgent_alert',
      recipient: 'child',
      childId,
      parentId,
      messageId: message.id,
      body,
      priority: 'urgent',
    }, URGENT_PUSH_OPTIONS) || delivered;
  }
  if (delivered) await db.update(alerts).set({ deliveredAt: new Date() }).where(eq(alerts.id, alert.id));
  return { cooldown: false as const, message: decryptMessageRow(r.rows[0] as { body: unknown }), delivered };
}
