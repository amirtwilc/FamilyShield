import { and, eq } from 'drizzle-orm';
import { db } from '@/db/client';
import { childParentLinks, parents } from '@/db/schema';

export async function monitoringInfo(childId: string) {
  const monitors = await db.select({
    parentId: parents.id,
    email: parents.email,
    displayName: childParentLinks.displayName,
    role: childParentLinks.role,
  }).from(childParentLinks)
    .innerJoin(parents, eq(childParentLinks.parentId, parents.id))
    .where(eq(childParentLinks.childId, childId));
  return { childId, monitors };
}

export async function monitorLink(childId: string, parentId: string) {
  const [link] = await db.select({
    childId: childParentLinks.childId,
    parentId: childParentLinks.parentId,
    displayName: childParentLinks.displayName,
    role: childParentLinks.role,
  }).from(childParentLinks)
    .where(and(eq(childParentLinks.childId, childId), eq(childParentLinks.parentId, parentId)));
  return link ?? null;
}

export async function firstMonitorParentId(childId: string): Promise<string | null> {
  const [link] = await db.select({ parentId: childParentLinks.parentId })
    .from(childParentLinks)
    .where(eq(childParentLinks.childId, childId))
    .orderBy(childParentLinks.createdAt)
    .limit(1);
  return link?.parentId ?? null;
}
