import { describe, it, expect, beforeAll } from 'vitest';
import { resetDb } from '../helpers/db';
import { seedParent, seedChild, seedDevice } from '../helpers/factories';
import { signAccess } from '@/lib/auth/jwt';
import { GET as parentList, POST as parentSend } from '@/app/api/children/[id]/messages/route';
import { GET as kidList, POST as kidSend } from '@/app/api/device/messages/route';
import { GET as kidMonitorList, POST as kidMonitorSend } from '@/app/api/device/monitors/[parentId]/messages/route';
import { GET as summary } from '@/app/api/messages/summary/route';
import { db } from '@/db/client';
import { childParentLinks } from '@/db/schema';

beforeAll(async () => { await resetDb(); });

describe('parent ⇄ kid chat', () => {
  it('exchanges messages both ways and tracks read state', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    const { token: dtok } = await seedDevice(c.id);
    const ptok = await signAccess(p.id);
    const ctx = { params: Promise.resolve({ id: c.id }) };
    const pAuth = { authorization: `Bearer ${ptok}` };
    const dAuth = { authorization: `Bearer ${dtok}` };

    // Parent sends a message.
    const sent = await parentSend(new Request('http://t/', { method: 'POST', headers: pAuth, body: JSON.stringify({ body: 'Where are you?' }) }), ctx);
    expect(sent.status).toBe(201);
    expect((await sent.json()).sender).toBe('parent');

    // Kid sees it (and the GET marks the parent message read).
    const kidView = await kidList(new Request('http://t/', { headers: dAuth }));
    const kidMsgs = (await kidView.json()).messages;
    expect(kidMsgs).toHaveLength(1);
    expect(kidMsgs[0].body).toBe('Where are you?');

    // Kid replies.
    const reply = await kidSend(new Request('http://t/', { method: 'POST', headers: dAuth, body: JSON.stringify({ body: 'At school!' }) }));
    expect(reply.status).toBe(201);
    expect((await reply.json()).sender).toBe('child');

    // Parent lists with markRead → both messages, child reply now read.
    const pView = await parentList(new Request('http://t/?markRead=1', { headers: pAuth }), ctx);
    const pMsgs = (await pView.json()).messages;
    expect(pMsgs.map((m: any) => m.body)).toEqual(['Where are you?', 'At school!']);
    expect(pMsgs.find((m: any) => m.sender === 'child').read_at).not.toBeNull();
  });

  it('empty body is rejected', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    const ptok = await signAccess(p.id);
    const r = await parentSend(new Request('http://t/', {
      method: 'POST', headers: { authorization: `Bearer ${ptok}` }, body: JSON.stringify({ body: '   ' }),
    }), { params: Promise.resolve({ id: c.id }) });
    expect(r.status).toBe(400);
  });

  it('pages older history with a before cursor', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    const ptok = await signAccess(p.id);
    const ctx = { params: Promise.resolve({ id: c.id }) };
    const send = (body: string) => parentSend(new Request('http://t/', {
      method: 'POST', headers: { authorization: `Bearer ${ptok}` }, body: JSON.stringify({ body }),
    }), ctx);
    for (let i = 0; i < 60; i++) await send(`m${i}`);

    const first = await (await parentList(new Request('http://t/', { headers: { authorization: `Bearer ${ptok}` } }), ctx)).json();
    expect(first.messages).toHaveLength(50);          // newest page caps at 50
    expect(first.nextCursor).toBeTruthy();

    const older = await (await parentList(new Request('http://t/?before=' + encodeURIComponent(first.nextCursor), { headers: { authorization: `Bearer ${ptok}` } }), ctx)).json();
    expect(older.messages).toHaveLength(10);          // the remaining 10
    expect(older.nextCursor).toBeNull();

    // the two pages are disjoint and together cover all 60 messages
    const all = new Set([...first.messages, ...older.messages].map((m: any) => m.body));
    expect(all.size).toBe(60);
  });

  it('conversation summary returns last message + unread per child in one call', async () => {
    const p = await seedParent();
    const c1 = await seedChild(p.id, 'A'); const c2 = await seedChild(p.id, 'B');
    const { token: d1 } = await seedDevice(c1.id);
    const ptok = await signAccess(p.id);
    const J = (body: unknown, token: string) => new Request('http://t/', { method: 'POST', headers: { authorization: `Bearer ${token}` }, body: JSON.stringify(body) });

    await parentSend(J({ body: 'hi' }, ptok), { params: Promise.resolve({ id: c1.id }) });
    await kidSend(J({ body: 'yo' }, d1)); // unread child message on c1

    const conv = (await (await summary(new Request('http://t/', { headers: { authorization: `Bearer ${ptok}` } }))).json()).conversations;
    expect(conv).toHaveLength(2);
    const a = conv.find((x: any) => x.childId === c1.id);
    expect(a.last.body).toBe('yo');
    expect(a.unread).toBe(1);
    const b = conv.find((x: any) => x.childId === c2.id);
    expect(b.last).toBeNull();
    expect(b.unread).toBe(0);
  });

  it('scopes kid chat to a selected monitoring parent', async () => {
    const p1 = await seedParent('chat_p1@test.io');
    const p2 = await seedParent('chat_p2@test.io');
    const c = await seedChild(p1.id, 'Mia');
    await db.insert(childParentLinks).values({ childId: c.id, parentId: p2.id, displayName: 'Mimi', role: 'caregiver' });
    const { token: dtok } = await seedDevice(c.id);
    const t1 = await signAccess(p1.id);
    const t2 = await signAccess(p2.id);
    const ctx = { params: Promise.resolve({ id: c.id }) };

    await parentSend(new Request('http://t/', { method: 'POST', headers: { authorization: `Bearer ${t1}` }, body: JSON.stringify({ body: 'p1' }) }), ctx);
    await parentSend(new Request('http://t/', { method: 'POST', headers: { authorization: `Bearer ${t2}` }, body: JSON.stringify({ body: 'p2' }) }), ctx);

    const kidP2 = await kidMonitorList(new Request('http://t/', { headers: { authorization: `Bearer ${dtok}` } }),
      { params: Promise.resolve({ parentId: p2.id }) });
    expect((await kidP2.json()).messages.map((m: any) => m.body)).toEqual(['p2']);

    await kidMonitorSend(new Request('http://t/', {
      method: 'POST', headers: { authorization: `Bearer ${dtok}` }, body: JSON.stringify({ body: 'reply to p2' }),
    }), { params: Promise.resolve({ parentId: p2.id }) });

    const p1View = await parentList(new Request('http://t/', { headers: { authorization: `Bearer ${t1}` } }), ctx);
    expect((await p1View.json()).messages.map((m: any) => m.body)).toEqual(['p1']);
    const p2View = await parentList(new Request('http://t/', { headers: { authorization: `Bearer ${t2}` } }), ctx);
    expect((await p2View.json()).messages.map((m: any) => m.body)).toEqual(['p2', 'reply to p2']);
  });
});
