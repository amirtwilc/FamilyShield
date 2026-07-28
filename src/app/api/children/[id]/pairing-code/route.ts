import { randomInt } from 'node:crypto';
import { and, isNull, lte } from 'drizzle-orm';
import { db } from '@/db/client';
import { pairingCodes } from '@/db/schema';
import { requireParent } from '@/lib/auth/parent';
import { assertChildOwned } from '@/lib/ownership';
import { ok, err } from '@/lib/http';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string }> };
const CODE_GENERATION_ATTEMPTS = 20;

export async function POST(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const ttlMin = Number(process.env.PAIRING_CODE_TTL_MIN ?? 10);
  if (!Number.isFinite(ttlMin) || ttlMin <= 0) return err('configuration_error', 'Pairing is temporarily unavailable', 503);
  const expiresAt = new Date(Date.now() + ttlMin * 60_000);

  // Expired rows no longer need to reserve a code in the partial unique index.
  await db.update(pairingCodes).set({ consumedAt: new Date() }).where(and(
    isNull(pairingCodes.consumedAt),
    lte(pairingCodes.expiresAt, new Date()),
  ));

  for (let attempt = 0; attempt < CODE_GENERATION_ATTEMPTS; attempt++) {
    const code = String(randomInt(100000, 1_000_000));
    const inserted = await db.insert(pairingCodes)
      .values({ childId: id, createdByParentId: a.parentId, code, expiresAt })
      .onConflictDoNothing()
      .returning({ id: pairingCodes.id });
    if (inserted.length > 0) return ok({ code, expiresAt: expiresAt.toISOString() }, 201);
  }
  return err('code_space_busy', 'Could not allocate a pairing code; please try again', 503);
}
