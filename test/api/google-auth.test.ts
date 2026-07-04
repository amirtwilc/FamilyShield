import { describe, it, expect, beforeAll } from 'vitest';
import { eq } from 'drizzle-orm';
import { resetDb } from '../helpers/db';
import { seedParent } from '../helpers/factories';
import { resolveGoogleParent } from '@/lib/auth/google';
import { db } from '@/db/client';
import { parents } from '@/db/schema';

beforeAll(async () => { await resetDb(); });

describe('google sign-in parent resolution', () => {
  it('creates a passwordless parent for a brand-new Google account', async () => {
    const id = await resolveGoogleParent({ email: 'new@gmail.com', sub: 'g-1', emailVerified: true });
    const [row] = await db.select().from(parents).where(eq(parents.id, id));
    expect(row.email).toBe('new@gmail.com');
    expect(row.googleSub).toBe('g-1');
    expect(row.passwordHash).toBeNull();
  });

  it('returns the same parent on repeat sign-in (matched by google subject)', async () => {
    const a = await resolveGoogleParent({ email: 'repeat@gmail.com', sub: 'g-2', emailVerified: true });
    const b = await resolveGoogleParent({ email: 'repeat@gmail.com', sub: 'g-2', emailVerified: true });
    expect(a).toBe(b);
  });

  it('links Google to an existing email/password account with the same email', async () => {
    const p = await seedParent('linkme@gmail.com');
    const id = await resolveGoogleParent({ email: 'linkme@gmail.com', sub: 'g-3', emailVerified: true });
    expect(id).toBe(p.id);
    const [row] = await db.select().from(parents).where(eq(parents.id, p.id));
    expect(row.googleSub).toBe('g-3');
    expect(row.passwordHash).not.toBeNull(); // password still works too
  });
});
