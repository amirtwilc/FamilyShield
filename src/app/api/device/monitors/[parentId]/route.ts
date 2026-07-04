import { and, eq } from 'drizzle-orm';
import { db } from '@/db/client';
import { childParentLinks, devices } from '@/db/schema';
import { requireDevice } from '@/lib/auth/device';
import { ok, err } from '@/lib/http';
import { monitoringInfo } from '@/lib/monitoring';
import { fireChildUnpaired } from '@/lib/alerts/engine';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ parentId: string }> };

export async function DELETE(req: Request, { params }: Ctx) {
  const a = await requireDevice(req); if ('response' in a) return a.response;
  const { parentId } = await params;

  const result = await db.transaction(async (tx) => {
    const [link] = await tx.select({ id: childParentLinks.id })
      .from(childParentLinks)
      .where(and(eq(childParentLinks.childId, a.device.childId), eq(childParentLinks.parentId, parentId)));
    if (!link) return { type: 'not_found' as const };

    const links = await tx.select({ id: childParentLinks.id })
      .from(childParentLinks)
      .where(eq(childParentLinks.childId, a.device.childId))
      .limit(2);
    if (links.length <= 1) {
      await tx.update(devices).set({ revokedAt: new Date() }).where(eq(devices.id, a.device.id));
      return { type: 'unpaired' as const };
    }
    await tx.delete(childParentLinks).where(eq(childParentLinks.id, link.id));
    return { type: 'removed' as const };
  });

  if (result.type === 'not_found') return err('not_found', 'Monitor not found', 404);
  if (result.type === 'unpaired') {
    await fireChildUnpaired(a.device);
    return ok({ unpaired: true, childId: a.device.childId, monitors: [] });
  }
  return ok({ unpaired: false, ...(await monitoringInfo(a.device.childId)) });
}
