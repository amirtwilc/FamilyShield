import { decodeJwt } from 'jose';
import { eq } from 'drizzle-orm';
import { db } from '../../db/client';
import { parents } from '../../db/schema';
import { verifyAccess } from './jwt';
import { err } from '../http';
import { firebaseAuthAdmin, requireAppCheck, type FirebaseIdentity } from './firebase';
import { legacyAuthEnabled, legacyAuthUnavailable } from './legacy';

function bearer(req: Request): string | null {
  const h = req.headers.get('authorization') ?? '';
  return h.startsWith('Bearer ') ? h.slice(7) : null;
}
function isFirebaseToken(token: string): boolean {
  try {
    const issuer = decodeJwt(token).iss;
    return typeof issuer === 'string' && issuer.startsWith('https://securetoken.google.com/');
  } catch {
    return false;
  }
}

export type FirebaseParent = { parentId: string; identity: FirebaseIdentity };

export async function requireFirebaseParent(req: Request): Promise<FirebaseParent | { response: Response }> {
  const t = bearer(req);
  if (!t) return { response: err('unauthorized', 'Missing bearer token', 401) };
  if (!(await requireAppCheck(req))) {
    return { response: err('invalid_app_check', 'App attestation failed', 401) };
  }
  try {
    const identity = await firebaseAuthAdmin().verifyIdToken(t);
    if (!identity.emailVerified) {
      return { response: err('email_unverified', 'Verify your email before continuing', 403) };
    }
    const matches = await db.select({ id: parents.id, email: parents.email })
      .from(parents)
      .where(eq(parents.firebaseUid, identity.uid));
    if (matches.length !== 1 || matches[0]!.email.trim().toLowerCase() !== identity.email) {
      return { response: err('auth_mapping_required', 'Complete account setup before continuing', 409) };
    }
    return { parentId: matches[0]!.id, identity };
  } catch {
    return { response: err('unauthorized', 'Invalid or revoked token', 401) };
  }
}

export async function requireParent(req: Request): Promise<{ parentId: string } | { response: Response }> {
  const t = bearer(req);
  if (!t) return { response: err('unauthorized', 'Missing bearer token', 401) };
  if (isFirebaseToken(t)) return requireFirebaseParent(req);
  if (!legacyAuthEnabled()) return { response: legacyAuthUnavailable() };
  try {
    const parentId = await verifyAccess(t);
    const [parent] = await db.select({ id: parents.id }).from(parents).where(eq(parents.id, parentId));
    if (!parent) return { response: err('unauthorized', 'Account no longer exists', 401) };
    return { parentId };
  }
  catch { return { response: err('unauthorized', 'Invalid token', 401) }; }
}
