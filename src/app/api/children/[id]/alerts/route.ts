import { and, eq, lt, or, desc, isNull } from 'drizzle-orm';
import { db } from '@/db/client';
import { alerts } from '@/db/schema';
import { requireParent } from '@/lib/auth/parent';
import { assertChildOwned } from '@/lib/ownership';
import { ok, err } from '@/lib/http';
import { decodeCursor, encodeCursor } from '@/lib/pagination';
import { parseQuery } from '@/lib/validate';
import { alertQuery } from '@/lib/schemas/parent';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string }> };

export async function GET(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const q = parseQuery(new URL(req.url), alertQuery); if ('response' in q) return q.response;
  const limit = q.data.limit ?? 50;
  const cur = q.data.cursor ? decodeCursor(q.data.cursor) : null;
  if (q.data.cursor && !cur) return err('validation_error', 'Invalid cursor', 400);
  if (cur && (!Number.isFinite(Date.parse(cur.recordedAt)) || !UUID_RE.test(cur.id))) {
    return err('validation_error', 'Invalid cursor', 400);
  }

  const rows = await db.select().from(alerts).where(and(
    eq(alerts.childId, id),
    or(eq(alerts.parentId, a.parentId), isNull(alerts.parentId)),
    cur ? or(
      lt(alerts.createdAt, new Date(cur.recordedAt)),
      and(eq(alerts.createdAt, new Date(cur.recordedAt)), lt(alerts.id, cur.id)),
    ) : undefined,
  )).orderBy(desc(alerts.createdAt), desc(alerts.id)).limit(limit + 1);

  const page = rows.slice(0, limit);
  const last = page[page.length - 1];
  const next = rows.length > limit && last
    ? encodeCursor({ recordedAt: last.createdAt.toISOString(), id: last.id }) : null;
  return ok({ alerts: page, nextCursor: next });
}

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
