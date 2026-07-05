import { eq, sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { devices } from '@/db/schema';
import { ensureLocationPartition } from '@/db/partitions';
import { requireDevice } from '@/lib/auth/device';
import { fireLowBatteryIfNeeded } from '@/lib/alerts/engine';
import { ok } from '@/lib/http';
import { parseBody } from '@/lib/validate';
import { deviceTelemetrySchema } from '@/lib/schemas/telemetry';

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
  }
  if (p.data.app_usage) {
    update.appUsageAccessGranted = p.data.app_usage.access_granted;
    update.appUsageAccessCheckedAt = new Date();
  }

  let locationInserted = 0;
  if (p.data.location) {
    const pt = p.data.location;
    await ensureLocationPartition(new Date(pt.recorded_at.slice(0, 7) + '-01T00:00:00Z'));
    const r = await db.execute(sql`
      INSERT INTO locations (device_id, geom, speed, accuracy, battery_level, recorded_at)
      VALUES (${deviceId}, ST_SetSRID(ST_MakePoint(${pt.lng}, ${pt.lat}), 4326),
              ${pt.speed ?? null}, ${pt.accuracy ?? null}, ${pt.battery_level ?? null}, ${pt.recorded_at})
      ON CONFLICT (device_id, recorded_at) DO NOTHING`);
    locationInserted = r.rowCount ?? 0;
    await db.execute(sql`
      UPDATE devices SET
        last_location = ST_SetSRID(ST_MakePoint(${pt.lng}, ${pt.lat}), 4326),
        last_location_at = ${pt.recorded_at},
        battery_level = COALESCE(${pt.battery_level ?? null}, battery_level)
      WHERE id = ${deviceId}`);
  }

  await db.update(devices).set(update).where(eq(devices.id, deviceId));

  const usageItems = p.data.app_usage?.items ?? [];
  let appUsageInserted = 0;
  if (usageItems.length > 0) {
    const byKey = new Map<string, typeof usageItems[number]>();
    for (const it of usageItems) byKey.set(`${it.app}|${it.day ?? ''}`, it);
    const items = [...byKey.values()];
    const rows = items.map((it) => sql`(${a.device.childId}, ${it.app}, ${it.category}, ${it.minutes}, COALESCE(${it.day ?? null}::date, CURRENT_DATE))`);
    await db.execute(sql`
      INSERT INTO app_usage (child_id, app, category, minutes, day)
      VALUES ${sql.join(rows, sql`, `)}
      ON CONFLICT (child_id, app, day) DO UPDATE SET minutes = EXCLUDED.minutes, category = EXCLUDED.category`);
    appUsageInserted = items.length;
  }

  const [fresh] = await db.select().from(devices).where(eq(devices.id, deviceId));
  await fireLowBatteryIfNeeded(fresh);
  return ok({ ok: true, locationInserted, appUsageInserted });
}
