import { sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { requireParent } from '@/lib/auth/parent';
import { assertChildOwned } from '@/lib/ownership';
import { ok, err } from '@/lib/http';
import { retentionCutoffForChild } from '@/lib/retention';
import { movementModeForSpeed } from '@/lib/movement';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string }> };

export async function GET(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const cutoff = await retentionCutoffForChild(id);
  const r = await db.execute(sql`
    SELECT ST_Y(d.last_location) AS lat, ST_X(d.last_location) AS lng,
           d.last_location_at AS recorded_at, latest.speed
    FROM devices d
    LEFT JOIN LATERAL (
      SELECT l.speed
      FROM locations l
      WHERE l.device_id = d.id AND l.recorded_at = d.last_location_at
      ORDER BY l.id DESC
      LIMIT 1
    ) latest ON TRUE
    WHERE d.child_id = ${id} AND d.last_location IS NOT NULL
      AND d.last_location_at >= ${cutoff.toISOString()}
    ORDER BY d.last_location_at DESC LIMIT 1`);
  const row = r.rows[0] as any;
  if (!row) return ok(null);
  return ok({
    lat: row.lat,
    lng: row.lng,
    recordedAt: new Date(row.recorded_at).toISOString(),
    movementMode: movementModeForSpeed(row.speed),
  });
}
