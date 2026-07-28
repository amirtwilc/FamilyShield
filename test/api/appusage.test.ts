import { describe, it, expect, beforeAll, beforeEach, afterEach } from 'vitest';
import { resetDb } from '../helpers/db';
import { seedParent, seedChild, seedDevice } from '../helpers/factories';
import { signAccess } from '@/lib/auth/jwt';
import { GET as usageGet } from '@/app/api/children/[id]/app-usage/route';
import { POST as usagePost } from '@/app/api/device/app-usage/route';
import { GET as limitsGet, POST as limitsPost } from '@/app/api/children/[id]/app-usage/limits/route';
import { PATCH as limitPatch, DELETE as limitDelete } from '@/app/api/children/[id]/app-usage/limits/[limitId]/route';
import { db } from '@/db/client';
import { alerts, childParentLinks } from '@/db/schema';
import { eq } from 'drizzle-orm';
import { resetSender, setSender, type PushOptions } from '@/lib/alerts/fcm';

beforeAll(async () => { await resetDb(); });
let pushes: Array<{ token: string; title: string; body: string; data?: Record<string, string>; options?: PushOptions }> = [];
beforeEach(() => {
  pushes = [];
  setSender({
    async send(token, title, body, data, options) {
      pushes.push({ token, title, body, data, options });
      return true;
    },
  });
});
afterEach(() => resetSender());

describe('app usage', () => {
  it('kid reports app usage and parent reads the breakdown', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    const { token: dtok } = await seedDevice(c.id);
    const ptok = await signAccess(p.id);
    const ctx = { params: Promise.resolve({ id: c.id }) };

    const r = await usagePost(new Request('http://t/', { method: 'POST', headers: { authorization: `Bearer ${dtok}` },
      body: JSON.stringify({ items: [
        { package_name: 'com.google.android.youtube', app: 'YouTube', category: 'Entertainment', minutes: 80 },
        { package_name: 'com.roblox.client', app: 'Roblox', category: 'Games', minutes: 45 },
        { package_name: 'com.whatsapp', app: 'WhatsApp', category: 'Social', minutes: 30 },
      ] }) }));
    expect(r.status).toBe(200);

    const g = await usageGet(new Request('http://t/', { headers: { authorization: `Bearer ${ptok}` } }), ctx);
    const data = await g.json();
    expect(data.totalTodayMin).toBe(155);
    expect(data.apps[0]).toMatchObject({ packageName: 'com.google.android.youtube', app: 'YouTube', min: 80 });
    expect(data.hiddenTodayMin).toBeUndefined();
    expect(data.week).toHaveLength(7);
    expect(data.week[6].min).toBe(155); // today is the last bar
    expect(data.dayDetails).toHaveLength(7);
    expect(data.dayDetails[6]).toMatchObject({ totalMin: 155 });
    expect(data.dayDetails[6].apps[0]).toMatchObject({ app: 'YouTube', min: 80 });
  });

  it('re-reporting the same app/day upserts instead of duplicating', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    const { token: dtok } = await seedDevice(c.id);
    const ptok = await signAccess(p.id);
    const ctx = { params: Promise.resolve({ id: c.id }) };
    const post = (min: number) => usagePost(new Request('http://t/', { method: 'POST', headers: { authorization: `Bearer ${dtok}` },
      body: JSON.stringify({ items: [{ app: 'TikTok', category: 'Entertainment', minutes: min }] }) }));
    await post(20); await post(35);
    const g = await usageGet(new Request('http://t/', { headers: { authorization: `Bearer ${ptok}` } }), ctx);
    expect((await g.json()).totalTodayMin).toBe(35);
  });

  it('parent can read the app breakdown for a selected day', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    const { token: dtok } = await seedDevice(c.id);
    const ptok = await signAccess(p.id);
    const ctx = { params: Promise.resolve({ id: c.id }) };
    const selectedDay = '2026-06-23';

    await usagePost(new Request('http://t/', { method: 'POST', headers: { authorization: `Bearer ${dtok}` },
      body: JSON.stringify({ items: [
        { package_name: 'com.video', app: 'Video', category: 'Entertainment', minutes: 90, day: selectedDay },
        { package_name: 'com.today', app: 'Today App', category: 'Games', minutes: 20 },
      ] }) }));

    const g = await usageGet(new Request(`http://t/?date=${selectedDay}`, { headers: { authorization: `Bearer ${ptok}` } }), ctx);
    const data = await g.json();
    expect(data.selectedDay).toBe(selectedDay);
    expect(data.totalTodayMin).toBe(90);
    expect(data.apps).toHaveLength(1);
    expect(data.apps[0]).toMatchObject({ app: 'Video', min: 90 });
  });

  it('drops system activity and sessions shorter than 5 minutes', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    const { token: dtok } = await seedDevice(c.id);
    const ptok = await signAccess(p.id);
    const ctx = { params: Promise.resolve({ id: c.id }) };

    const r = await usagePost(new Request('http://t/', { method: 'POST', headers: { authorization: `Bearer ${dtok}` },
      body: JSON.stringify({ items: [
        { package_name: 'com.nianticlabs.pokemongo', app: 'Pokemon GO', category: 'Games', minutes: 25, is_relevant: true },
        { package_name: 'com.mi.android.globallauncher', app: 'POCO Launcher', category: 'System', minutes: 12, is_relevant: false, hidden_reason: 'launcher' },
        { package_name: 'com.example.short', app: 'Short Game', category: 'Games', minutes: 4, is_relevant: true },
      ] }) }));
    expect(r.status).toBe(200);
    expect(await r.json()).toMatchObject({ inserted: 1 });

    const g = await usageGet(new Request('http://t/', { headers: { authorization: `Bearer ${ptok}` } }), ctx);
    const data = await g.json();
    expect(data.totalTodayMin).toBe(25);
    expect(data.apps).toHaveLength(1);
    expect(data.apps[0]).toMatchObject({ app: 'Pokemon GO', packageName: 'com.nianticlabs.pokemongo' });
    expect(data.hiddenTodayMin).toBeUndefined();
    expect(data.hiddenActivityCount).toBeUndefined();
    expect(data.hiddenApps).toBeUndefined();
  });

  it('creates lists updates and deletes parent-scoped usage limits', async () => {
    const p1 = await seedParent(); const p2 = await seedParent(); const c = await seedChild(p1.id);
    await db.insert(childParentLinks).values({ childId: c.id, parentId: p2.id, displayName: 'Kid' });
    const t1 = await signAccess(p1.id); const t2 = await signAccess(p2.id);
    const ctx = { params: Promise.resolve({ id: c.id }) };

    const createdR = await limitsPost(new Request('http://t/', { method: 'POST', headers: { authorization: `Bearer ${t1}` },
      body: JSON.stringify({ type: 'total', limitMinutes: 60 }) }), ctx);
    expect(createdR.status).toBe(201);
    const created = (await createdR.json()).limit;

    const p1List = await limitsGet(new Request('http://t/', { headers: { authorization: `Bearer ${t1}` } }), ctx);
    expect((await p1List.json()).limits).toHaveLength(1);
    const p2List = await limitsGet(new Request('http://t/', { headers: { authorization: `Bearer ${t2}` } }), ctx);
    expect((await p2List.json()).limits).toHaveLength(0);

    const blocked = await limitPatch(new Request('http://t/', { method: 'PATCH', headers: { authorization: `Bearer ${t2}` },
      body: JSON.stringify({ limitMinutes: 30 }) }), { params: Promise.resolve({ id: c.id, limitId: created.id }) });
    expect(blocked.status).toBe(404);

    const updated = await limitPatch(new Request('http://t/', { method: 'PATCH', headers: { authorization: `Bearer ${t1}` },
      body: JSON.stringify({ limitMinutes: 90, active: false }) }), { params: Promise.resolve({ id: c.id, limitId: created.id }) });
    expect((await updated.json()).limit).toMatchObject({ limitMinutes: 90, active: false });

    const deleted = await limitDelete(new Request('http://t/', { method: 'DELETE', headers: { authorization: `Bearer ${t1}` } }),
      { params: Promise.resolve({ id: c.id, limitId: created.id }) });
    expect(deleted.status).toBe(200);
  });

  it('fires a total app-usage limit push once per day', async () => {
    const p = await seedParent(); const c = await seedChild(p.id, 'Mia');
    const { token: dtok } = await seedDevice(c.id);
    const ptok = await signAccess(p.id);
    const ctx = { params: Promise.resolve({ id: c.id }) };

    await limitsPost(new Request('http://t/', { method: 'POST', headers: { authorization: `Bearer ${ptok}` },
      body: JSON.stringify({ type: 'total', limitMinutes: 60 }) }), ctx);
    await usagePost(new Request('http://t/', { method: 'POST', headers: { authorization: `Bearer ${dtok}` },
      body: JSON.stringify({ items: [{ package_name: 'com.video', app: 'Video', category: 'Entertainment', minutes: 50 }] }) }));
    expect(pushes).toHaveLength(0);

    await usagePost(new Request('http://t/', { method: 'POST', headers: { authorization: `Bearer ${dtok}` },
      body: JSON.stringify({ items: [{ package_name: 'com.video', app: 'Video', category: 'Entertainment', minutes: 70 }] }) }));
    await usagePost(new Request('http://t/', { method: 'POST', headers: { authorization: `Bearer ${dtok}` },
      body: JSON.stringify({ items: [{ package_name: 'com.video', app: 'Video', category: 'Entertainment', minutes: 80 }] }) }));

    expect(pushes).toHaveLength(1);
    expect(pushes[0]).toEqual(expect.objectContaining({ token: p.fcmToken }));
    expect(pushes[0].data).toEqual(expect.objectContaining({
      type: 'app_usage_limit_exceeded',
      childId: c.id,
      childName: 'Mia',
      limitType: 'total',
      usageMinutes: '70',
      limitMinutes: '60',
    }));
    expect(pushes[0].body).toContain('Mia');
    expect(pushes[0].options?.includeNotification).toBe(false);
    expect((await db.select().from(alerts).where(eq(alerts.type, 'app_usage_limit_exceeded')))).toHaveLength(1);
  });

  it('fires an app-specific limit by package once per day', async () => {
    const p = await seedParent(); const c = await seedChild(p.id, 'Noa');
    const { token: dtok } = await seedDevice(c.id);
    const ptok = await signAccess(p.id);
    const ctx = { params: Promise.resolve({ id: c.id }) };

    await limitsPost(new Request('http://t/', { method: 'POST', headers: { authorization: `Bearer ${ptok}` },
      body: JSON.stringify({ type: 'app', packageName: 'com.youtube', app: 'YouTube', category: 'Entertainment', limitMinutes: 30 }) }), ctx);
    await usagePost(new Request('http://t/', { method: 'POST', headers: { authorization: `Bearer ${dtok}` },
      body: JSON.stringify({ items: [
        { package_name: 'com.youtube', app: 'YouTube', category: 'Entertainment', minutes: 35 },
        { package_name: 'com.roblox', app: 'Roblox', category: 'Games', minutes: 90 },
      ] }) }));
    await usagePost(new Request('http://t/', { method: 'POST', headers: { authorization: `Bearer ${dtok}` },
      body: JSON.stringify({ items: [{ package_name: 'com.youtube', app: 'YouTube', category: 'Entertainment', minutes: 40 }] }) }));

    expect(pushes).toHaveLength(1);
    expect(pushes[0].data).toEqual(expect.objectContaining({
      type: 'app_usage_limit_exceeded',
      childName: 'Noa',
      limitType: 'app',
      app: 'YouTube',
      packageName: 'com.youtube',
      usageMinutes: '35',
      limitMinutes: '30',
    }));
  });

  it('does not fire inactive limits or old-day reports', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    const { token: dtok } = await seedDevice(c.id);
    const ptok = await signAccess(p.id);
    const ctx = { params: Promise.resolve({ id: c.id }) };

    await limitsPost(new Request('http://t/', { method: 'POST', headers: { authorization: `Bearer ${ptok}` },
      body: JSON.stringify({ type: 'total', limitMinutes: 10, active: false }) }), ctx);
    await usagePost(new Request('http://t/', { method: 'POST', headers: { authorization: `Bearer ${dtok}` },
      body: JSON.stringify({ items: [{ package_name: 'com.video', app: 'Video', category: 'Entertainment', minutes: 20 }] }) }));
    expect(pushes).toHaveLength(0);

    await limitsPost(new Request('http://t/', { method: 'POST', headers: { authorization: `Bearer ${ptok}` },
      body: JSON.stringify({ type: 'total', limitMinutes: 10, active: true }) }), ctx);
    await usagePost(new Request('http://t/', { method: 'POST', headers: { authorization: `Bearer ${dtok}` },
      body: JSON.stringify({ items: [{ package_name: 'com.old', app: 'Old', category: 'Games', minutes: 30, day: '2026-06-23' }] }) }));
    expect(pushes).toHaveLength(0);
  });
});
