import { sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { requireParent } from '@/lib/auth/parent';
import { assertChildOwned } from '@/lib/ownership';
import { parseQuery } from '@/lib/validate';
import { ok, err } from '@/lib/http';
import { decodeCursor, encodeCursor } from '@/lib/pagination';
import { historyQuery } from '@/lib/schemas/parent';
import { retentionCutoffForChild } from '@/lib/retention';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string }> };

export async function GET(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const q = parseQuery(new URL(req.url), historyQuery); if ('response' in q) return q.response;

  const dayStart = `${q.data.date}T00:00:00Z`;
  const dayEnd = `${q.data.date}T23:59:59.999Z`;
  const cutoff = await retentionCutoffForChild(id);
  const start = new Date(Math.max(new Date(dayStart).getTime(), cutoff.getTime())).toISOString();
  const cur = q.data.cursor ? decodeCursor(q.data.cursor) : null;
  const limit = q.data.limit ?? 200;

  const r = await db.execute(sql`
    SELECT l.id, ST_Y(l.geom) AS lat, ST_X(l.geom) AS lng, l.recorded_at, l.speed, l.accuracy
    FROM locations l
    JOIN devices d ON d.id = l.device_id
    WHERE d.child_id = ${id}
      AND l.recorded_at >= ${start} AND l.recorded_at <= ${dayEnd}
      ${cur ? sql`AND (l.recorded_at, l.id) > (${cur.recordedAt}, ${cur.id})` : sql``}
    ORDER BY l.recorded_at ASC, l.id ASC
    LIMIT ${limit + 1}`);

  const rows = r.rows as any[];
  const page = rows.slice(0, limit);
  const next = rows.length > limit
    ? encodeCursor({ recordedAt: new Date(page[page.length - 1].recorded_at).toISOString(), id: page[page.length - 1].id })
    : null;
  return ok({
    points: page.map((x) => ({
      lat: x.lat, lng: x.lng, recordedAt: new Date(x.recorded_at).toISOString(),
      speed: x.speed, accuracy: x.accuracy,
    })),
    nextCursor: next,
  });
}
