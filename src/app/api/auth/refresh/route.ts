import { parseBody } from '@/lib/validate';
import { ok, err } from '@/lib/http';
import { verifyRefresh, signAccess, signRefresh } from '@/lib/auth/jwt';
import { refreshSchema } from '@/lib/schemas/auth';

export const runtime = 'nodejs';

export async function POST(req: Request) {
  const p = await parseBody(req, refreshSchema);
  if ('response' in p) return p.response;
  try {
    const parentId = await verifyRefresh(p.data.refreshToken);
    return ok({ accessToken: await signAccess(parentId), refreshToken: await signRefresh(parentId) });
  } catch { return err('unauthorized', 'Invalid refresh token', 401); }
}
