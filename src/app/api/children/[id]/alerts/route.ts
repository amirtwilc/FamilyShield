import { and, eq, lt, or, desc } from 'drizzle-orm';
import { db } from '@/db/client';
import { alerts } from '@/db/schema';
import { requireParent } from '@/lib/auth/parent';
import { assertChildOwned } from '@/lib/ownership';
import { ok, err } from '@/lib/http';
import { decodeCursor, encodeCursor } from '@/lib/pagination';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string }> };

export async function GET(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const url = new URL(req.url);
  const limit = Math.min(Number(url.searchParams.get('limit') ?? 50), 200);
  const cur = url.searchParams.get('cursor') ? decodeCursor(url.searchParams.get('cursor')!) : null;

  const rows = await db.select().from(alerts).where(and(
    eq(alerts.childId, id),
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
