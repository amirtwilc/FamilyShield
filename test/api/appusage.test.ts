import { describe, it, expect, beforeAll } from 'vitest';
import { resetDb } from '../helpers/db';
import { seedParent, seedChild, seedDevice } from '../helpers/factories';
import { signAccess } from '@/lib/auth/jwt';
import { GET as usageGet } from '@/app/api/children/[id]/app-usage/route';
import { POST as usagePost } from '@/app/api/device/app-usage/route';

beforeAll(async () => { await resetDb(); });

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
    expect(data.hiddenTodayMin).toBe(0);
    expect(data.week).toHaveLength(7);
    expect(data.week[6].min).toBe(155); // today is the last bar
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

  it('hides system activity from the default parent breakdown', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    const { token: dtok } = await seedDevice(c.id);
    const ptok = await signAccess(p.id);
    const ctx = { params: Promise.resolve({ id: c.id }) };

    const r = await usagePost(new Request('http://t/', { method: 'POST', headers: { authorization: `Bearer ${dtok}` },
      body: JSON.stringify({ items: [
        { package_name: 'com.nianticlabs.pokemongo', app: 'Pokemon GO', category: 'Games', minutes: 25, is_relevant: true },
        { package_name: 'com.mi.android.globallauncher', app: 'POCO Launcher', category: 'System', minutes: 12, is_relevant: false, hidden_reason: 'launcher' },
      ] }) }));
    expect(r.status).toBe(200);

    const g = await usageGet(new Request('http://t/', { headers: { authorization: `Bearer ${ptok}` } }), ctx);
    const data = await g.json();
    expect(data.totalTodayMin).toBe(25);
    expect(data.apps).toHaveLength(1);
    expect(data.apps[0]).toMatchObject({ app: 'Pokemon GO', packageName: 'com.nianticlabs.pokemongo' });
    expect(data.hiddenTodayMin).toBe(12);
    expect(data.hiddenActivityCount).toBe(1);
    expect(data.hiddenApps[0]).toMatchObject({ app: 'POCO Launcher', hiddenReason: 'launcher' });
  });
});
