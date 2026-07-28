import { describe, it, expect, beforeAll } from 'vitest';
import { resetDb } from '../helpers/db';
import { POST as register } from '@/app/api/auth/register/route';
import { POST as login } from '@/app/api/auth/login/route';
import { POST as refresh } from '@/app/api/auth/refresh/route';
import { db } from '@/db/client';
import { parents } from '@/db/schema';
import { eq } from 'drizzle-orm';

beforeAll(async () => { await resetDb(); });
const post = (body: unknown) => new Request('http://t/', { method: 'POST', body: JSON.stringify(body) });

describe('auth api', () => {
  it('registers and logs in', async () => {
    const r1 = await register(post({ email: 'a@b.io', password: 'password123' }));
    expect(r1.status).toBe(201);
    const t1 = await r1.json();
    expect(t1.accessToken).toBeTruthy();

    const r2 = await login(post({ email: 'a@b.io', password: 'password123' }));
    expect(r2.status).toBe(200);

    const r3 = await login(post({ email: 'a@b.io', password: 'wrong' }));
    expect(r3.status).toBe(401);
  });

  it('rejects duplicate email', async () => {
    await register(post({ email: 'dup@b.io', password: 'password123' }));
    const r = await register(post({ email: 'dup@b.io', password: 'password123' }));
    expect(r.status).toBe(409);
  });

  it('rejects short password', async () => {
    const r = await register(post({ email: 'x@b.io', password: 'short' }));
    expect(r.status).toBe(400);
  });

  it('does not refresh tokens after the parent account is deleted', async () => {
    const registered = await register(post({ email: 'deleted@b.io', password: 'password123' }));
    const tokens = await registered.json();
    await db.delete(parents).where(eq(parents.email, 'deleted@b.io'));

    const response = await refresh(post({ refreshToken: tokens.refreshToken }));

    expect(response.status).toBe(401);
  });
});
