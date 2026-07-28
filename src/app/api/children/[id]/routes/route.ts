import { sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { requireParent } from '@/lib/auth/parent';
import { assertChildOwned } from '@/lib/ownership';
import { ok, err } from '@/lib/http';
import { analyzeRoutes, type GpsPoint } from '@/lib/routes';
import { retentionCutoffForChild } from '@/lib/retention';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string }> };
export const HISTORY_ROUTE_LOOKBACK_DAYS = 14;

/** Detected routes for a child: recurring routes (with departure/return points)
 *  plus the individual trips, derived from the location history. */
export async function GET(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);

  const requestedDays = Number(new URL(req.url).searchParams.get('days') ?? HISTORY_ROUTE_LOOKBACK_DAYS);
  const days = Number.isFinite(requestedDays)
    ? Math.min(Math.max(requestedDays, 1), 90)
    : HISTORY_ROUTE_LOOKBACK_DAYS;
  const retentionCutoff = await retentionCutoffForChild(id);
  const requestedCutoff = new Date(Date.now() - days * 24 * 60 * 60_000);
  const cutoff = new Date(Math.max(retentionCutoff.getTime(), requestedCutoff.getTime()));
  const r = await db.execute(sql`
    SELECT recent.lat, recent.lng, recent.recorded_at
    FROM (
      SELECT ST_Y(l.geom) AS lat, ST_X(l.geom) AS lng, l.recorded_at
      FROM locations l JOIN devices d ON d.id = l.device_id
      WHERE d.child_id = ${id} AND l.recorded_at >= ${cutoff.toISOString()}
      ORDER BY l.recorded_at DESC
      LIMIT 5000
    ) recent
    ORDER BY recent.recorded_at ASC`);

  const points: GpsPoint[] = (r.rows as any[]).map((x) => ({
    lat: x.lat, lng: x.lng, at: new Date(x.recorded_at).toISOString(),
  }));
  const { stops, trips, frequent } = analyzeRoutes(points);
  return ok({ frequent, trips, stops });
}
