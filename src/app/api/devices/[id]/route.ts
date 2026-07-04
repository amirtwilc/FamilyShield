import { and, eq } from 'drizzle-orm';
import { db } from '@/db/client';
import { childParentLinks, devices } from '@/db/schema';
import { requireParent } from '@/lib/auth/parent';
import { ok, err } from '@/lib/http';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string }> };

export async function DELETE(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  const [owned] = await db.select({ id: devices.id })
    .from(devices).innerJoin(childParentLinks, eq(devices.childId, childParentLinks.childId))
    .where(and(eq(devices.id, id), eq(childParentLinks.parentId, a.parentId)));
  if (!owned) return err('not_found', 'Device not found', 404);
  await db.update(devices).set({ revokedAt: new Date() }).where(eq(devices.id, id));
  return ok({ ok: true });
}
