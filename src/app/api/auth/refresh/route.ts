import { eq } from 'drizzle-orm';
import { db } from '@/db/client';
import { parents } from '@/db/schema';
import { parseBody } from '@/lib/validate';
import { ok, err } from '@/lib/http';
import { verifyRefresh, signAccess, signRefresh } from '@/lib/auth/jwt';
import { refreshSchema } from '@/lib/schemas/auth';
import { legacyAuthEnabled, legacyAuthUnavailable } from '@/lib/auth/legacy';

export const runtime = 'nodejs';

export async function POST(req: Request) {
  if (!legacyAuthEnabled()) return legacyAuthUnavailable();
  const p = await parseBody(req, refreshSchema);
  if ('response' in p) return p.response;
  try {
    const parentId = await verifyRefresh(p.data.refreshToken);
    const [parent] = await db.select({ id: parents.id }).from(parents).where(eq(parents.id, parentId));
    if (!parent) return err('unauthorized', 'Account no longer exists', 401);
    return ok({ accessToken: await signAccess(parentId), refreshToken: await signRefresh(parentId) });
  } catch { return err('unauthorized', 'Invalid refresh token', 401); }
}
