import { describe, it, expect, beforeAll } from 'vitest';
import { resetDb } from '../helpers/db';
import { seedParent } from '../helpers/factories';
import { db } from '@/db/client';
import { childParentLinks, children } from '@/db/schema';
import { signAccess } from '@/lib/auth/jwt';
import { POST as createChild, GET as listChildren } from '@/app/api/children/route';
import { DELETE as deleteChild, PATCH as renameChild } from '@/app/api/children/[id]/route';
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
    const listed = await r2.json();
    expect(listed.children).toHaveLength(1);
    expect(listed.children[0].avatar).toBe('fox');

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

  it('assigns unused avatars first and allows editing the avatar', async () => {
    const p = await seedParent('avatars@test.io'); const tok = await signAccess(p.id);
    const made = [];
    for (let i = 0; i < 5; i++) {
      const r = await createChild(auth(tok, { displayName: `Kid ${i}` }));
      made.push(await r.json());
    }
    expect(made.map((c) => c.avatar)).toEqual(['fox', 'panda', 'tiger', 'unicorn', 'bunny']);

    const updated = await renameChild(auth(tok, { displayName: 'Kid 0', avatar: 'owl' }), { params: Promise.resolve({ id: made[0].id }) });
    expect(updated.status).toBe(200);
    expect((await updated.json()).avatar).toBe('owl');
  });

  it('deletes a child from the parent list and cascades when no parents remain', async () => {
    const p = await seedParent('delete-child@test.io'); const tok = await signAccess(p.id);
    const created = await createChild(auth(tok, { displayName: 'Mia' }));
    const child = await created.json();

    const deleted = await deleteChild(new Request('http://t/', { method: 'DELETE', headers: { authorization: `Bearer ${tok}` } }),
      { params: Promise.resolve({ id: child.id }) });
    expect(deleted.status).toBe(200);

    const listed = await listChildren(new Request('http://t/', { headers: { authorization: `Bearer ${tok}` } }));
    expect((await listed.json()).children).toHaveLength(0);
    expect(await db.select().from(children)).not.toContainEqual(expect.objectContaining({ id: child.id }));
  });
});
