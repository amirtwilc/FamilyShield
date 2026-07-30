import { firebaseAuthAdmin } from '@/lib/auth/firebase';
import { requireFirebaseParent } from '@/lib/auth/parent';
import { err, ok } from '@/lib/http';

export const runtime = 'nodejs';
const RECENT_AUTH_SECONDS = 5 * 60;

export async function POST(req: Request) {
  const auth = await requireFirebaseParent(req);
  if ('response' in auth) return auth.response;
  const nowSeconds = Math.floor(Date.now() / 1000);
  if (nowSeconds - auth.identity.authTime > RECENT_AUTH_SECONDS) {
    return err('recent_login_required', 'Sign in again before signing out all devices', 403);
  }
  await firebaseAuthAdmin().revokeRefreshTokens(auth.identity.uid);
  return ok({ revoked: true });
}
