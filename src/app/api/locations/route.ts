import { sql } from 'drizzle-orm';
import { eq } from 'drizzle-orm';
import { db } from '@/db/client';
import { devices } from '@/db/schema';
import { requireDevice } from '@/lib/auth/device';
import { parseBody } from '@/lib/validate';
import { ok } from '@/lib/http';
import { ensureLocationPartition } from '@/db/partitions';
import { fireLowBatteryIfNeeded } from '@/lib/alerts/engine';
import { locationBatch } from '@/lib/schemas/locations';

export const runtime = 'nodejs';

export async function POST(req: Request) {
  const a = await requireDevice(req); if ('response' in a) return a.response;
  const p = await parseBody(req, locationBatch); if ('response' in p) return p.response;
  const deviceId = a.device.id;

  // ensure partitions for all referenced months
  const months = new Set(p.data.points.map((pt) => pt.recorded_at.slice(0, 7)));
  for (const ym of months) await ensureLocationPartition(new Date(`${ym}-01T00:00:00Z`));

  let inserted = 0;
  for (const pt of p.data.points) {
    const r = await db.execute(sql`
      INSERT INTO locations (device_id, geom, speed, accuracy, battery_level, recorded_at)
      VALUES (${deviceId}, ST_SetSRID(ST_MakePoint(${pt.lng}, ${pt.lat}), 4326),
              ${pt.speed ?? null}, ${pt.accuracy ?? null}, ${pt.battery_level ?? null}, ${pt.recorded_at})
      ON CONFLICT (device_id, recorded_at) DO NOTHING`);
    inserted += r.rowCount ?? 0;
  }

  // denormalize latest point
  const latest = p.data.points.reduce((a, b) => (a.recorded_at >= b.recorded_at ? a : b));
  await db.execute(sql`
    UPDATE devices SET
      last_location = ST_SetSRID(ST_MakePoint(${latest.lng}, ${latest.lat}), 4326),
      last_location_at = ${latest.recorded_at},
      last_seen_at = now(),
      battery_level = COALESCE(${latest.battery_level ?? null}, battery_level)
    WHERE id = ${deviceId}`);

  const [fresh] = await db.select().from(devices).where(eq(devices.id, deviceId));
  await fireLowBatteryIfNeeded(fresh);
  return ok({ inserted });
}
