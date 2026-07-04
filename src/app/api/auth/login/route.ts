import { eq } from 'drizzle-orm';
import { db } from '@/db/client';
import { parents } from '@/db/schema';
import { parseBody } from '@/lib/validate';
import { ok, err } from '@/lib/http';
import { verifyPassword } from '@/lib/auth/password';
import { signAccess, signRefresh } from '@/lib/auth/jwt';
import { loginSchema } from '@/lib/schemas/auth';
import { memoryLimiter, clientKey, tooMany } from '@/lib/ratelimit';

export const runtime = 'nodejs';

const loginLimiter = memoryLimiter(10, 60_000);

export async function POST(req: Request) {
  if (!loginLimiter.check(clientKey(req, 'auth_login')).allowed) return tooMany();
  const p = await parseBody(req, loginSchema);
  if ('response' in p) return p.response;
  const [row] = await db.select().from(parents).where(eq(parents.email, p.data.email));
  // No password hash → a Google-only account; reject password login.
  if (!row || !row.passwordHash || !(await verifyPassword(row.passwordHash, p.data.password)))
    return err('invalid_credentials', 'Invalid email or password', 401);
  return ok({ accessToken: await signAccess(row.id), refreshToken: await signRefresh(row.id) });
}
