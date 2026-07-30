import { and, eq, sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { parents } from '@/db/schema';
import { firebaseAuthAdmin, requireAppCheck } from '@/lib/auth/firebase';
import { err, ok } from '@/lib/http';

export const runtime = 'nodejs';

function bearer(req: Request): string | null {
  const header = req.headers.get('authorization') ?? '';
  return header.startsWith('Bearer ') ? header.slice(7) : null;
}

export async function POST(req: Request) {
  const token = bearer(req);
  if (!token) return err('unauthorized', 'Missing bearer token', 401);
  if (!(await requireAppCheck(req))) return err('invalid_app_check', 'App attestation failed', 401);

  let identity;
  try {
    identity = await firebaseAuthAdmin().verifyIdToken(token);
  } catch {
    return err('unauthorized', 'Invalid or revoked token', 401);
  }
  if (!identity.emailVerified) {
    return err('email_unverified', 'Verify your email before continuing', 403);
  }

  try {
    const parentId = await db.transaction(async (tx) => {
      const [byUid] = await tx.select().from(parents).where(eq(parents.firebaseUid, identity.uid));
      if (byUid) {
        if (byUid.email.trim().toLowerCase() !== identity.email) throw new Error('identity_conflict');
        if (!byUid.authMigratedAt) {
          await tx.update(parents)
            .set({ authMigratedAt: new Date() })
            .where(eq(parents.id, byUid.id));
        }
        return byUid.id;
      }

      if (identity.providerId === 'google.com' && identity.providerUid) {
        const [byGoogle] = await tx.select().from(parents)
          .where(eq(parents.googleSub, identity.providerUid));
        if (byGoogle) {
          if (byGoogle.firebaseUid && byGoogle.firebaseUid !== identity.uid) throw new Error('identity_conflict');
          if (byGoogle.email.trim().toLowerCase() !== identity.email) throw new Error('identity_conflict');
          await tx.update(parents)
            .set({ firebaseUid: identity.uid, authMigratedAt: new Date() })
            .where(and(eq(parents.id, byGoogle.id), sql`${parents.firebaseUid} IS NULL`));
          return byGoogle.id;
        }
      }

      const byEmail = await tx.select().from(parents)
        .where(sql`lower(btrim(${parents.email})) = ${identity.email}`);
      if (byEmail.length > 1) throw new Error('identity_conflict');
      if (byEmail.length === 1) {
        const existing = byEmail[0]!;
        // Pre-provisioning deliberately uses the Neon UUID as Firebase UID.
        // Refuse to merge a separately-established Firebase identity by email.
        if (identity.uid !== existing.id || existing.firebaseUid && existing.firebaseUid !== identity.uid) {
          throw new Error('identity_conflict');
        }
        await tx.update(parents)
          .set({ firebaseUid: identity.uid, authMigratedAt: new Date() })
          .where(eq(parents.id, existing.id));
        return existing.id;
      }

      const [created] = await tx.insert(parents)
        .values({
          email: identity.email,
          firebaseUid: identity.uid,
          authMigratedAt: new Date(),
          passwordHash: null,
          googleSub: identity.providerId === 'google.com' ? identity.providerUid : null,
        })
        .returning({ id: parents.id });
      return created!.id;
    });
    return ok({ parentId });
  } catch (error) {
    if (error instanceof Error && error.message === 'identity_conflict') {
      return err(
        'identity_conflict',
        'This sign-in cannot be linked automatically. Sign in with the existing method and link Google.',
        409,
      );
    }
    throw error;
  }
}
