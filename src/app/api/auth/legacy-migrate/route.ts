import { eq, sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { parents } from '@/db/schema';
import {
  firebaseAuthAdmin,
  isFirebaseUserNotFound,
  requireAppCheck,
} from '@/lib/auth/firebase';
import { verifyPassword } from '@/lib/auth/password';
import { err, ok } from '@/lib/http';
import { databaseLimiter, clientKey, tooMany } from '@/lib/ratelimit';
import { legacyMigrateSchema } from '@/lib/schemas/auth';
import { parseBody } from '@/lib/validate';

export const runtime = 'nodejs';

const ipLimiter = databaseLimiter(5, 15 * 60_000);
const accountLimiter = databaseLimiter(5, 15 * 60_000);
const DUMMY_HASH = '$argon2id$v=19$m=65536,t=3,p=4$tE1TXQqgSr7B8aH2RmTKaA$qohjaF94F5SunaRkxHsnR+jfq1HBfa7i7m8vQFjlMDo';

const genericFailure = () => err('migration_failed', 'Unable to sign in with those credentials', 401);

export async function POST(req: Request) {
  if (!(await requireAppCheck(req, true))) return err('invalid_app_check', 'App attestation failed', 401);
  if (!(await ipLimiter.check(clientKey(req, 'legacy_migrate_ip'))).allowed) return tooMany();

  const parsed = await parseBody(req, legacyMigrateSchema);
  if ('response' in parsed) return parsed.response;
  if (!(await accountLimiter.check(`legacy_migrate_account:${parsed.data.email}`)).allowed) return tooMany();

  const matches = await db.select().from(parents)
    .where(sql`lower(btrim(${parents.email})) = ${parsed.data.email}`);
  if (matches.length !== 1 || !matches[0]!.passwordHash) {
    await verifyPassword(DUMMY_HASH, parsed.data.password);
    return genericFailure();
  }
  const parent = matches[0]!;
  if (!(await verifyPassword(parent.passwordHash!, parsed.data.password))) return genericFailure();

  const admin = firebaseAuthAdmin();
  const expectedUid = parent.firebaseUid ?? parent.id;
  try {
    let firebaseUser;
    try {
      firebaseUser = await admin.getUser(expectedUid);
    } catch (error) {
      if (!isFirebaseUserNotFound(error)) throw error;
      firebaseUser = await admin.createUser({
        uid: parent.id,
        email: parent.email.trim().toLowerCase(),
        emailVerified: parent.googleSub !== null,
        password: parsed.data.password,
      });
    }
    if (firebaseUser.email?.trim().toLowerCase() !== parsed.data.email) return genericFailure();

    await admin.updateUser(firebaseUser.uid, { password: parsed.data.password });
    const customToken = await admin.createCustomToken(firebaseUser.uid);

    await db.update(parents).set({
      firebaseUid: firebaseUser.uid,
      authMigratedAt: new Date(),
      passwordHash: null,
    }).where(eq(parents.id, parent.id));

    return ok({ customToken });
  } catch {
    return genericFailure();
  }
}
