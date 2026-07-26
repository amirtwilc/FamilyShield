import { requireParent } from '@/lib/auth/parent';
import { ok, err } from '@/lib/http';
import { assertChildOwned } from '@/lib/ownership';
import { urgentAlertSchema } from '@/lib/schemas/sos';
import { sendUrgentAlertToChild } from '@/lib/sos';
import { parseBody } from '@/lib/validate';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string }> };

export async function POST(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const p = await parseBody(req, urgentAlertSchema); if ('response' in p) return p.response;
  const result = await sendUrgentAlertToChild(id, a.parentId, p.data.body);
  if (result.cooldown) return err('rate_limited', 'Please wait before sending another urgent alert', 429, { retryAfterSeconds: result.retryAfterSeconds });
  return ok({ message: result.message, delivered: result.delivered }, 201);
}
