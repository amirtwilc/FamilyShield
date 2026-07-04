import { describe, it, expect, beforeAll } from 'vitest';
import { resetDb } from '../helpers/db';
import { seedParent } from '../helpers/factories';
import { db } from '@/db/client';
import { childParentLinks } from '@/db/schema';
import { signAccess } from '@/lib/auth/jwt';
import { POST as createChild, GET as listChildren } from '@/app/api/children/route';
import { PATCH as renameChild } from '@/app/api/children/[id]/route';
import { POST as genCode } from '@/app/api/children/[id]/pairing-code/route';

beforeAll(async () => { await resetDb(); });
const auth = (t: string, body?: unknown) => new Request('http://t/', {
  method: 'POST', headers: { authorization: `Bearer ${t}` },
  body: body ? JSON.stringify(body) : undefined,
});

describe('children api', () => {
  it('creates, lists, and generates a pairing code', async () => {
    const p = await seedParent(); const tok = await signAccess(p.id);
    const r1 = await createChild(auth(tok, { displayName: 'Mia' }));
    expect(r1.status).toBe(201);
    const child = await r1.json();

    const r2 = await listChildren(new Request('http://t/', { headers: { authorization: `Bearer ${tok}` } }));
    expect((await r2.json()).children).toHaveLength(1);

    const r3 = await genCode(auth(tok), { params: Promise.resolve({ id: child.id }) });
    const code = await r3.json();
    expect(code.code).toMatch(/^\d{6}$/);
  });

  it('rejects unauthenticated', async () => {
    const r = await createChild(new Request('http://t/', { method: 'POST', body: '{}' }));
    expect(r.status).toBe(401);
  });

  it('returns 404 (not 500) for a malformed child id', async () => {
    const p = await seedParent(); const tok = await signAccess(p.id);
    const r = await genCode(auth(tok), { params: Promise.resolve({ id: 'not-a-uuid' }) });
    expect(r.status).toBe(404);
  });

  it('returns each linked parent their own child display name', async () => {
    const p1 = await seedParent('children_p1@test.io'); const t1 = await signAccess(p1.id);
    const p2 = await seedParent('children_p2@test.io'); const t2 = await signAccess(p2.id);
    const created = await createChild(auth(t1, { displayName: 'Mia' }));
    const child = await created.json();
    await db.insert(childParentLinks).values({
      childId: child.id, parentId: p2.id, displayName: 'Mimi', role: 'caregiver',
    });

    expect((await (await listChildren(new Request('http://t/', { headers: { authorization: `Bearer ${t1}` } }))).json()).children[0].displayName).toBe('Mia');
    expect((await (await listChildren(new Request('http://t/', { headers: { authorization: `Bearer ${t2}` } }))).json()).children[0].displayName).toBe('Mimi');

    await renameChild(auth(t2, { displayName: 'Kiddo' }), { params: Promise.resolve({ id: child.id }) });
    expect((await (await listChildren(new Request('http://t/', { headers: { authorization: `Bearer ${t1}` } }))).json()).children[0].displayName).toBe('Mia');
    expect((await (await listChildren(new Request('http://t/', { headers: { authorization: `Bearer ${t2}` } }))).json()).children[0].displayName).toBe('Kiddo');
  });

  it('enforces the free tier five child limit', async () => {
    const p = await seedParent('limit@test.io'); const tok = await signAccess(p.id);
    for (let i = 0; i < 5; i++) {
      expect((await createChild(auth(tok, { displayName: `Kid ${i}` }))).status).toBe(201);
    }

    const denied = await createChild(auth(tok, { displayName: 'Kid 6' }));
    expect(denied.status).toBe(403);
    const body = await denied.json();
    expect(body.error.code).toBe('tier_limit_exceeded');
  });
});
