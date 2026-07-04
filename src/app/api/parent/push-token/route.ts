import { eq } from 'drizzle-orm';
import { db } from '@/db/client';
import { parents } from '@/db/schema';
import { requireParent } from '@/lib/auth/parent';
import { parseBody } from '@/lib/validate';
import { ok } from '@/lib/http';
import { pushTokenSchema } from '@/lib/schemas/parent';

export const runtime = 'nodejs';

export async function POST(req: Request) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const p = await parseBody(req, pushTokenSchema); if ('response' in p) return p.response;
  await db.update(parents).set({ fcmToken: p.data.fcm_token }).where(eq(parents.id, a.parentId));
  return ok({ ok: true });
}
