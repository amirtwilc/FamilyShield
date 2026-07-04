import { parseBody } from '@/lib/validate';
import { ok, err } from '@/lib/http';
import { signAccess, signRefresh } from '@/lib/auth/jwt';
import { googleSchema } from '@/lib/schemas/auth';
import { verifyGoogleIdToken, resolveGoogleParent } from '@/lib/auth/google';
import { memoryLimiter, clientKey, tooMany } from '@/lib/ratelimit';

export const runtime = 'nodejs';

const googleLimiter = memoryLimiter(20, 60_000);

/** Sign in with a Google ID token. Verifies it, then finds/links/creates the
 *  parent and returns our normal access + refresh tokens. */
export async function POST(req: Request) {
  if (!googleLimiter.check(clientKey(req, 'auth_google')).allowed) return tooMany();
  const p = await parseBody(req, googleSchema);
  if ('response' in p) return p.response;

  let claims;
  try {
    claims = await verifyGoogleIdToken(p.data.idToken);
  } catch {
    return err('invalid_token', 'Invalid or expired Google token', 401);
  }
  if (!claims.emailVerified) return err('email_unverified', 'Google account email is not verified', 401);

  const parentId = await resolveGoogleParent(claims);
  return ok({ accessToken: await signAccess(parentId), refreshToken: await signRefresh(parentId) });
}
