import { eq } from 'drizzle-orm';
import { db } from '@/db/client';
import { parents } from '@/db/schema';
import { parseBody } from '@/lib/validate';
import { ok, err } from '@/lib/http';
import { hashPassword } from '@/lib/auth/password';
import { signAccess, signRefresh } from '@/lib/auth/jwt';
import { registerSchema } from '@/lib/schemas/auth';
import { memoryLimiter, clientKey, tooMany } from '@/lib/ratelimit';

export const runtime = 'nodejs';

const registerLimiter = memoryLimiter(10, 60_000);

export async function POST(req: Request) {
  if (!registerLimiter.check(clientKey(req, 'auth_register')).allowed) return tooMany();
  const p = await parseBody(req, registerSchema);
  if ('response' in p) return p.response;
  const existing = await db.select().from(parents).where(eq(parents.email, p.data.email));
  if (existing.length) return err('email_taken', 'Email already registered', 409);
  const [row] = await db.insert(parents)
    .values({ email: p.data.email, passwordHash: await hashPassword(p.data.password) })
    .returning();
  return ok({ accessToken: await signAccess(row.id), refreshToken: await signRefresh(row.id) }, 201);
}
