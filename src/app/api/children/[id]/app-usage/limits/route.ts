import { and, eq, isNull } from 'drizzle-orm';
import { db } from '@/db/client';
import { appUsageLimits } from '@/db/schema';
import { requireParent } from '@/lib/auth/parent';
import { assertChildOwned } from '@/lib/ownership';
import { ok, err } from '@/lib/http';
import { parseBody } from '@/lib/validate';
import { appUsageLimitSchema } from '@/lib/schemas/appusage';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string }> };

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

export async function GET(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);

  const rows = await db.select().from(appUsageLimits).where(and(
    eq(appUsageLimits.parentId, a.parentId),
    eq(appUsageLimits.childId, id),
  ));
  return ok({ limits: rows.map(serializeLimit) });
}

export async function POST(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const p = await parseBody(req, appUsageLimitSchema); if ('response' in p) return p.response;

  const body = p.data;
  const existingWhere = body.type === 'total'
    ? and(
        eq(appUsageLimits.parentId, a.parentId),
        eq(appUsageLimits.childId, id),
        eq(appUsageLimits.type, 'total'),
      )
    : body.packageName
      ? and(
          eq(appUsageLimits.parentId, a.parentId),
          eq(appUsageLimits.childId, id),
          eq(appUsageLimits.type, 'app'),
          eq(appUsageLimits.packageName, body.packageName),
        )
      : and(
          eq(appUsageLimits.parentId, a.parentId),
          eq(appUsageLimits.childId, id),
          eq(appUsageLimits.type, 'app'),
          isNull(appUsageLimits.packageName),
          eq(appUsageLimits.app, body.app!),
        );

  const [existing] = await db.select().from(appUsageLimits).where(existingWhere).limit(1);
  if (existing) {
    const [updated] = await db.update(appUsageLimits).set({
      packageName: body.type === 'app' ? body.packageName ?? null : null,
      app: body.type === 'app' ? body.app ?? existing.app : null,
      category: body.type === 'app' ? body.category ?? existing.category : null,
      limitMinutes: body.limitMinutes,
      active: body.active ?? true,
      updatedAt: new Date(),
    }).where(eq(appUsageLimits.id, existing.id)).returning();
    return ok({ limit: serializeLimit(updated) });
  }

  const [created] = await db.insert(appUsageLimits).values({
    parentId: a.parentId,
    childId: id,
    type: body.type,
    packageName: body.type === 'app' ? body.packageName ?? null : null,
    app: body.type === 'app' ? body.app ?? null : null,
    category: body.type === 'app' ? body.category ?? null : null,
    limitMinutes: body.limitMinutes,
    active: body.active ?? true,
  }).returning();
  return ok({ limit: serializeLimit(created) }, 201);
}
