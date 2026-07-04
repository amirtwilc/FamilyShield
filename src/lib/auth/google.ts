import { jwtVerify, createRemoteJWKSet } from 'jose';
import { eq } from 'drizzle-orm';
import { db } from '../../db/client';
import { parents } from '../../db/schema';
import { requireEnv } from '../env';

// Google's public signing keys (cached/rotated by jose).
const GOOGLE_JWKS = createRemoteJWKSet(new URL('https://www.googleapis.com/oauth2/v3/certs'));

export type GoogleClaims = { email: string; sub: string; emailVerified: boolean };

/**
 * Verifies a Google ID token (issuer + signature + audience) and returns the
 * essential claims. Audience must match our configured OAuth client id; an
 * unset GOOGLE_CLIENT_ID fails closed (no tokens are accepted).
 */
export async function verifyGoogleIdToken(idToken: string): Promise<GoogleClaims> {
  const clientId = requireEnv('GOOGLE_CLIENT_ID');
  const { payload } = await jwtVerify(idToken, GOOGLE_JWKS, {
    issuer: ['https://accounts.google.com', 'accounts.google.com'],
    audience: clientId,
  });
  const email = typeof payload.email === 'string' ? payload.email.toLowerCase() : null;
  if (!email || typeof payload.sub !== 'string') throw new Error('Google token missing email/sub');
  return { email, sub: payload.sub, emailVerified: payload.email_verified === true };
}

/**
 * Maps verified Google claims to a parent id, creating or linking as needed:
 * 1) existing parent already linked to this Google subject → use it;
 * 2) existing parent with the same email → link Google to it;
 * 3) otherwise → create a passwordless Google parent.
 */
export async function resolveGoogleParent(claims: GoogleClaims): Promise<string> {
  const [bySub] = await db.select().from(parents).where(eq(parents.googleSub, claims.sub));
  if (bySub) return bySub.id;

  const [byEmail] = await db.select().from(parents).where(eq(parents.email, claims.email));
  if (byEmail) {
    if (byEmail.googleSub !== claims.sub) {
      await db.update(parents).set({ googleSub: claims.sub }).where(eq(parents.id, byEmail.id));
    }
    return byEmail.id;
  }

  const [created] = await db.insert(parents)
    .values({ email: claims.email, googleSub: claims.sub, passwordHash: null })
    .returning();
  return created.id;
}
