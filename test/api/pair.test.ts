import { describe, it, expect, beforeAll, beforeEach, afterEach } from 'vitest';
import { resetDb } from '../helpers/db';
import { seedDevice, seedParent, seedChild } from '../helpers/factories';
import { db } from '@/db/client';
import { alerts, childParentLinks, children, pairingCodes } from '@/db/schema';
import { POST as pair } from '@/app/api/pair/route';
import { GET as listChildren } from '@/app/api/children/route';
import { GET as monitoring } from '@/app/api/device/monitoring/route';
import { DELETE as removeMonitor, PATCH as renameMonitor } from '@/app/api/device/monitors/[parentId]/route';
import { GET as currentLocation } from '@/app/api/children/[id]/location/current/route';
import { signAccess } from '@/lib/auth/jwt';
import { resetSender, setSender } from '@/lib/alerts/fcm';

beforeAll(async () => { await resetDb(); });
let sentPushes: Array<{ token: string; title: string; body: string; data?: Record<string, string> }> = [];
beforeEach(() => {
  sentPushes = [];
  setSender({
    async send(token, title, body, data) {
      sentPushes.push({ token, title, body, data });
      return true;
    },
  });
});
afterEach(() => resetSender());
const post = (body: unknown, token?: string) => new Request('http://t/', {
  method: 'POST',
  headers: token ? { authorization: `Bearer ${token}` } : {},
  body: JSON.stringify(body),
});
const patch = (body: unknown, token: string) => new Request('http://t/', {
  method: 'PATCH',
  headers: { authorization: `Bearer ${token}` },
  body: JSON.stringify(body),
});
const get = (token: string) => new Request('http://t/', { headers: { authorization: `Bearer ${token}` } });
const ctx = (id: string) => ({ params: Promise.resolve({ id }) });

async function makeCode(childId: string, code = '123456', minsValid = 10) {
  await db.insert(pairingCodes).values({ childId, code, expiresAt: new Date(Date.now() + minsValid * 60000) });
}

describe('pairing', () => {
  it('pairs with a valid code, single-use', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    await makeCode(c.id, '111111');
    const r = await pair(post({ code: '111111', platform: 'android', model: 'Pixel', parentDisplayName: 'Mom' }));
    expect(r.status).toBe(201);
    expect((await r.json()).deviceToken).toBeTruthy();
    const r2 = await pair(post({ code: '111111', platform: 'android', parentDisplayName: 'Mom' }));
    expect(r2.status).toBe(400); // already consumed
  });

  it('rejects expired codes', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    await makeCode(c.id, '222222', -1);
    const r = await pair(post({ code: '222222', platform: 'android', parentDisplayName: 'Mom' }));
    expect(r.status).toBe(400);
  });

  it('enforces uniqueness for all active pairing codes', async () => {
    const first = await seedParent('unique-code-1@test.io');
    const second = await seedParent('unique-code-2@test.io');
    const firstChild = await seedChild(first.id);
    const secondChild = await seedChild(second.id);
    await makeCode(firstChild.id, '212121');

    await expect(makeCode(secondChild.id, '212121')).rejects.toMatchObject({
      cause: { code: '23505' },
    });
  });

  it('adds a second parent to an already paired child and removes the placeholder child', async () => {
    const p1 = await seedParent('p1_pair@test.io');
    const realChild = await seedChild(p1.id, 'Mia');
    const { token: deviceToken } = await seedDevice(realChild.id);

    const p2 = await seedParent('p2_pair@test.io');
    const placeholder = await seedChild(p2.id, 'Mimi');
    await db.insert(pairingCodes).values({
      childId: placeholder.id,
      createdByParentId: p2.id,
      code: '333333',
      expiresAt: new Date(Date.now() + 10 * 60000),
    });

    const r = await pair(post({ code: '333333', platform: 'android', parentDisplayName: 'Dad' }, deviceToken));
    expect(r.status).toBe(200);
    const body = await r.json();
    expect(body.childId).toBe(realChild.id);
    expect(body.monitors.map((m: any) => m.email).sort()).toEqual(['p1_pair@test.io', 'p2_pair@test.io']);
    expect(body.monitors.find((m: any) => m.email === 'p2_pair@test.io').parentDisplayName).toBe('Dad');

    const links = await db.select().from(childParentLinks);
    expect(links.some((l) => l.childId === realChild.id && l.parentId === p2.id && l.displayName === 'Mimi')).toBe(true);
    expect((await db.select().from(children)).some((c) => c.id === placeholder.id)).toBe(false);

    const p2Token = await signAccess(p2.id);
    expect((await currentLocation(get(p2Token), ctx(realChild.id))).status).toBe(200);

    const monitorView = await monitoring(get(deviceToken));
    expect(monitorView.status).toBe(200);
    expect((await monitorView.json()).monitors).toHaveLength(2);
  });

  it('rejects adding the same parent twice', async () => {
    const p = await seedParent('dup_pair@test.io');
    const c = await seedChild(p.id, 'Mia');
    const { token: deviceToken } = await seedDevice(c.id);
    await makeCode(c.id, '444444');

    const r = await pair(post({ code: '444444', platform: 'android', parentDisplayName: 'Mom' }, deviceToken));
    expect(r.status).toBe(400);
  });

  it('lets the kid device rename a monitoring parent', async () => {
    const p = await seedParent('rename-monitor@test.io');
    const c = await seedChild(p.id, 'Mia');
    const { token: deviceToken } = await seedDevice(c.id);

    const response = await renameMonitor(patch({ parentDisplayName: 'Ima' }, deviceToken),
      { params: Promise.resolve({ parentId: p.id }) });

    expect(response.status).toBe(200);
    const body = await response.json();
    expect(body.monitors).toHaveLength(1);
    expect(body.monitors[0]).toMatchObject({ email: 'rename-monitor@test.io', parentDisplayName: 'Ima' });
  });

  it('lets a kid device remove one monitor and fully unpairs after the last monitor', async () => {
    const p1 = await seedParent('remove_p1@test.io');
    const p2 = await seedParent('remove_p2@test.io');
    const c = await seedChild(p1.id, 'Mia');
    await db.insert(childParentLinks).values({ childId: c.id, parentId: p2.id, displayName: 'Mimi' });
    const { token: deviceToken } = await seedDevice(c.id);

    const removeP2 = await removeMonitor(new Request('http://t/', { method: 'DELETE', headers: { authorization: `Bearer ${deviceToken}` } }),
      { params: Promise.resolve({ parentId: p2.id }) });
    expect(removeP2.status).toBe(200);
    const afterP2 = await removeP2.json();
    expect(afterP2.unpaired).toBe(false);
    expect(afterP2.monitors).toHaveLength(1);
    expect(sentPushes.some((p) => p.token === p2.fcmToken && p.data?.type === 'child_unpaired')).toBe(true);

    const removeP1 = await removeMonitor(new Request('http://t/', { method: 'DELETE', headers: { authorization: `Bearer ${deviceToken}` } }),
      { params: Promise.resolve({ parentId: p1.id }) });
    expect(removeP1.status).toBe(200);
    expect((await removeP1.json()).unpaired).toBe(true);
    expect(sentPushes.some((p) => p.token === p1.fcmToken && p.data?.type === 'child_unpaired')).toBe(true);
    expect((await monitoring(get(deviceToken))).status).toBe(401);

    const p1Token = await signAccess(p1.id);
    const childrenAfterUnpair = await listChildren(get(p1Token));
    expect(childrenAfterUnpair.status).toBe(200);
    const visible = await childrenAfterUnpair.json();
    expect(visible.children).toHaveLength(1);
    expect(visible.children[0].id).toBe(c.id);
    expect(visible.children[0].devices[0].revokedAt).toBeTruthy();
    const unpairAlerts = await db.select().from(alerts);
    expect(unpairAlerts.some((a) => a.childId === c.id && a.type === 'child_unpaired')).toBe(true);
  });
});
