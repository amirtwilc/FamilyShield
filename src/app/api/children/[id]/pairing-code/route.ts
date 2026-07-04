import { db } from '@/db/client';
import { pairingCodes } from '@/db/schema';
import { requireParent } from '@/lib/auth/parent';
import { assertChildOwned } from '@/lib/ownership';
import { ok, err } from '@/lib/http';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string }> };

export async function POST(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const ttlMin = Number(process.env.PAIRING_CODE_TTL_MIN ?? 10);
  const code = String(Math.floor(100000 + Math.random() * 900000));
  const expiresAt = new Date(Date.now() + ttlMin * 60_000);
  await db.insert(pairingCodes).values({ childId: id, createdByParentId: a.parentId, code, expiresAt });
  return ok({ code, expiresAt: expiresAt.toISOString() }, 201);
}
