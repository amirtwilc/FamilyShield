import { and, eq } from 'drizzle-orm';
import { db } from '@/db/client';
import { childParentLinks, children } from '@/db/schema';

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export async function assertChildOwned(parentId: string, childId: string) {
  // A non-UUID path param would make Postgres throw (→ 500); treat it as a
  // miss so child-scoped routes return a clean 404 instead.
  if (!UUID_RE.test(childId)) return null;
  const [c] = await db.select({
    id: children.id,
    parentId: children.parentId,
    displayName: childParentLinks.displayName,
    createdAt: children.createdAt,
  }).from(children)
    .innerJoin(childParentLinks, eq(childParentLinks.childId, children.id))
    .where(and(eq(children.id, childId), eq(childParentLinks.parentId, parentId)));
  return c ?? null;
}
