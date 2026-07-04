import { and, eq } from 'drizzle-orm';
import { db } from '@/db/client';
import { safeZones } from '@/db/schema';
import { requireParent } from '@/lib/auth/parent';
import { assertChildOwned } from '@/lib/ownership';
import { ok, err } from '@/lib/http';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string; zoneId: string }> };

export async function DELETE(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id, zoneId } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const deleted = await db.delete(safeZones)
    .where(and(eq(safeZones.id, zoneId), eq(safeZones.childId, id))).returning();
  if (!deleted.length) return err('not_found', 'Zone not found', 404);
  return ok({ ok: true });
}
