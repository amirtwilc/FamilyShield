import { and, eq } from 'drizzle-orm';
import { db } from '@/db/client';
import { appUsageLimits } from '@/db/schema';
import { requireParent } from '@/lib/auth/parent';
import { assertChildOwned } from '@/lib/ownership';
import { ok, err } from '@/lib/http';
import { parseBody } from '@/lib/validate';
import { updateAppUsageLimitSchema } from '@/lib/schemas/appusage';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string; limitId: string }> };

function serializeLimit(row: typeof appUsageLimits.$inferSelect) {
  return {
    id: row.id,
    childId: row.childId,
    type: row.type,
    packageName: row.packageName,
    app: row.app,
    category: row.category,
    limitMinutes: row.limitMinutes,
    active: row.active,
    createdAt: row.createdAt,
    updatedAt: row.updatedAt,
  };
}

export async function PATCH(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id, limitId } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const p = await parseBody(req, updateAppUsageLimitSchema); if ('response' in p) return p.response;

  const [updated] = await db.update(appUsageLimits).set({
    ...(p.data.limitMinutes !== undefined ? { limitMinutes: p.data.limitMinutes } : {}),
    ...(p.data.active !== undefined ? { active: p.data.active } : {}),
    updatedAt: new Date(),
  }).where(and(
    eq(appUsageLimits.id, limitId),
    eq(appUsageLimits.parentId, a.parentId),
    eq(appUsageLimits.childId, id),
  )).returning();

  if (!updated) return err('not_found', 'Limit not found', 404);
  return ok({ limit: serializeLimit(updated) });
}

export async function DELETE(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id, limitId } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);

  const [deleted] = await db.delete(appUsageLimits).where(and(
    eq(appUsageLimits.id, limitId),
    eq(appUsageLimits.parentId, a.parentId),
    eq(appUsageLimits.childId, id),
  )).returning({ id: appUsageLimits.id });

  if (!deleted) return err('not_found', 'Limit not found', 404);
  return ok({ deleted: true });
}
