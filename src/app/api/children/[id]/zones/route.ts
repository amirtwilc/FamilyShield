import { sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { requireParent } from '@/lib/auth/parent';
import { assertChildOwned } from '@/lib/ownership';
import { parseBody } from '@/lib/validate';
import { ok, err } from '@/lib/http';
import { createZoneSchema } from '@/lib/schemas/zones';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string }> };

export async function POST(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const p = await parseBody(req, createZoneSchema); if ('response' in p) return p.response;
  const r = await db.execute(sql`
    INSERT INTO safe_zones (parent_id, source_child_id, name, center, radius_m, active, notify_on_enter, notify_on_exit, dwell_minutes)
    VALUES (${a.parentId}, ${id}, ${p.data.name}, ST_SetSRID(ST_MakePoint(${p.data.lng}, ${p.data.lat}), 4326),
            ${p.data.radiusM}, ${p.data.active ?? true}, ${p.data.notifyOnEnter ?? true}, ${p.data.notifyOnExit ?? true},
            ${p.data.dwellMinutes ?? null})
    RETURNING id, name, ST_Y(center) AS lat, ST_X(center) AS lng, radius_m, active,
      notify_on_enter, notify_on_exit, dwell_minutes, created_at`);
  return ok(r.rows[0], 201);
}

export async function GET(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const r = await db.execute(sql`
    SELECT id, name, ST_Y(center) AS lat, ST_X(center) AS lng, radius_m, active,
      notify_on_enter, notify_on_exit, dwell_minutes, created_at
    FROM safe_zones WHERE parent_id = ${a.parentId} ORDER BY created_at DESC, id DESC LIMIT 200`);
  return ok({ zones: r.rows });
}
