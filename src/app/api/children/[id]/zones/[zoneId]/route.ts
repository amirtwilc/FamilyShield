import { and, eq, sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { safeZones } from '@/db/schema';
import { requireParent } from '@/lib/auth/parent';
import { assertChildOwned } from '@/lib/ownership';
import { parseBody } from '@/lib/validate';
import { updateZoneSchema } from '@/lib/schemas/zones';
import { ok, err } from '@/lib/http';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string; zoneId: string }> };

export async function PATCH(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id, zoneId } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const p = await parseBody(req, updateZoneSchema); if ('response' in p) return p.response;

  const update: Partial<typeof safeZones.$inferInsert> = {};
  if (p.data.name !== undefined) update.name = p.data.name;
  if (p.data.radiusM !== undefined) update.radiusM = p.data.radiusM;
  if (p.data.active !== undefined) update.active = p.data.active;

  const updated = await db.update(safeZones).set(update)
    .where(and(eq(safeZones.id, zoneId), eq(safeZones.parentId, a.parentId))).returning({ id: safeZones.id });
  if (!updated.length) return err('not_found', 'Zone not found', 404);

  const r = await db.execute(sql`
    SELECT id, name, ST_Y(center) AS lat, ST_X(center) AS lng, radius_m, active,
      notify_on_enter, notify_on_exit, dwell_minutes, created_at
    FROM safe_zones WHERE id = ${zoneId} AND parent_id = ${a.parentId}`);
  return ok(r.rows[0]);
}

export async function DELETE(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id, zoneId } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const deleted = await db.delete(safeZones)
    .where(and(eq(safeZones.id, zoneId), eq(safeZones.parentId, a.parentId))).returning();
  if (!deleted.length) return err('not_found', 'Zone not found', 404);
  return ok({ ok: true });
}
