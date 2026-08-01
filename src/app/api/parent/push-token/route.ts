import { requireParent } from '@/lib/auth/parent';
import { parseBody } from '@/lib/validate';
import { ok } from '@/lib/http';
import { pushTokenSchema } from '@/lib/schemas/parent';
import { registerParentPushToken, unregisterParentPushToken } from '@/lib/parent-push';

export const runtime = 'nodejs';

export async function POST(req: Request) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const p = await parseBody(req, pushTokenSchema); if ('response' in p) return p.response;
  await registerParentPushToken(a.parentId, p.data.fcm_token);
  return ok({ ok: true });
}

export async function DELETE(req: Request) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const p = await parseBody(req, pushTokenSchema); if ('response' in p) return p.response;
  await unregisterParentPushToken(a.parentId, p.data.fcm_token);
  return ok({ ok: true });
}
