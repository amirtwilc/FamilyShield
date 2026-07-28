import { eq, sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { devices } from '@/db/schema';
import { ensureLocationPartition } from '@/db/partitions';
import { requireDevice } from '@/lib/auth/device';
import { fireLowBatteryIfNeeded, fireSafeZoneTransitions } from '@/lib/alerts/engine';
import { ok } from '@/lib/http';
import { parseBody } from '@/lib/validate';
import { deviceTelemetrySchema } from '@/lib/schemas/telemetry';
import { upsertAppUsageReport } from '@/lib/app-usage-ingest';

export const runtime = 'nodejs';

export async function POST(req: Request) {
  const a = await requireDevice(req); if ('response' in a) return a.response;
  const p = await parseBody(req, deviceTelemetrySchema); if ('response' in p) return p.response;
  const deviceId = a.device.id;

  const update: Partial<typeof devices.$inferInsert> = { lastSeenAt: new Date() };
  if (p.data.status) {
    update.batteryLevel = p.data.status.battery_level ?? a.device.batteryLevel;
    update.isCharging = p.data.status.is_charging ?? a.device.isCharging;
    update.fcmToken = p.data.status.fcm_token ?? a.device.fcmToken;
    if (p.data.status.p) {
      update.permissionStatus = p.data.status.p;
      update.permissionStatusCheckedAt = new Date();
      update.appUsageAccessGranted = Boolean(p.data.status.p.m & 16);
      update.appUsageAccessCheckedAt = new Date();
    }
  }
  if (p.data.app_usage) {
    update.appUsageAccessGranted = p.data.app_usage.access_granted;
    update.appUsageAccessCheckedAt = new Date();
  }

  let locationInserted = 0;
  const locationPoints = dedupeLocationPoints([
    ...(p.data.locations ?? []),
    ...(p.data.location ? [p.data.location] : []),
  ]);
  if (locationPoints.length > 0) {
    const months = new Set(locationPoints.map((pt) => pt.recorded_at.slice(0, 7)));
    for (const ym of months) await ensureLocationPartition(new Date(`${ym}-01T00:00:00Z`));

    for (const pt of locationPoints) {
      const r = await db.execute(sql`
        INSERT INTO locations (device_id, geom, speed, accuracy, battery_level, recorded_at)
        VALUES (${deviceId}, ST_SetSRID(ST_MakePoint(${pt.lng}, ${pt.lat}), 4326),
                ${pt.speed ?? null}, ${pt.accuracy ?? null}, ${pt.battery_level ?? null}, ${pt.recorded_at})
        ON CONFLICT (device_id, recorded_at) DO NOTHING`);
      locationInserted += r.rowCount ?? 0;
      if ((r.rowCount ?? 0) > 0) {
        await fireSafeZoneTransitions({ device: a.device, lat: pt.lat, lng: pt.lng, recordedAt: pt.recorded_at });
      }
    }

    const latest = locationPoints.reduce((a, b) => (a.recorded_at >= b.recorded_at ? a : b));
    await db.execute(sql`
      UPDATE devices SET
        last_location = CASE
          WHEN last_location_at IS NULL OR last_location_at < ${latest.recorded_at}
            THEN ST_SetSRID(ST_MakePoint(${latest.lng}, ${latest.lat}), 4326)
          ELSE last_location
        END,
        last_location_at = GREATEST(last_location_at, ${latest.recorded_at}),
        battery_level = CASE
          WHEN last_location_at IS NULL OR last_location_at < ${latest.recorded_at}
            THEN COALESCE(${latest.battery_level ?? null}, battery_level)
          ELSE battery_level
        END
      WHERE id = ${deviceId}`);
  }

  await db.update(devices).set(update).where(eq(devices.id, deviceId));

  const usageItems = p.data.app_usage?.items ?? [];
  let appUsageInserted = 0;
  if (usageItems.length > 0) {
    appUsageInserted = (await upsertAppUsageReport(a.device, usageItems)).inserted;
  }

  const [fresh] = await db.select().from(devices).where(eq(devices.id, deviceId));
  await fireLowBatteryIfNeeded(fresh);
  return ok({ ok: true, locationInserted, appUsageInserted });
}

function dedupeLocationPoints<T extends { recorded_at: string }>(points: T[]): T[] {
  const byRecordedAt = new Map<string, T>();
  for (const point of points) byRecordedAt.set(point.recorded_at, point);
  return [...byRecordedAt.values()].sort((a, b) => +new Date(a.recorded_at) - +new Date(b.recorded_at));
}
