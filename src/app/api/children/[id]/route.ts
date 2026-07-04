import { and, eq } from 'drizzle-orm';
import { db } from '@/db/client';
import { childParentLinks, children } from '@/db/schema';
import { requireParent } from '@/lib/auth/parent';
import { assertChildOwned } from '@/lib/ownership';
import { parseBody } from '@/lib/validate';
import { ok, err } from '@/lib/http';
import { createChildSchema } from '@/lib/schemas/children';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string }> };

export async function GET(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  const child = await assertChildOwned(a.parentId, id);
  if (!child) return err('not_found', 'Child not found', 404);
  return ok(child);
}

/** Rename a child (Family Settings → Edit profile). */
export async function PATCH(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const p = await parseBody(req, createChildSchema); if ('response' in p) return p.response;
  await db.update(childParentLinks).set({ displayName: p.data.displayName })
    .where(and(eq(childParentLinks.childId, id), eq(childParentLinks.parentId, a.parentId)));
  const [row] = await db.update(children).set({ displayName: p.data.displayName })
    .where(eq(children.id, id)).returning();
  return ok({ ...row, displayName: p.data.displayName });
}
