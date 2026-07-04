import { describe, it, expect, beforeAll, beforeEach } from 'vitest';
import { resetDb } from '../helpers/db';
import { seedParent, seedChild, seedDevice } from '../helpers/factories';
import { setSender } from '@/lib/alerts/fcm';
import { signAccess } from '@/lib/auth/jwt';
import { POST as upload } from '@/app/api/locations/route';
import { GET as current } from '@/app/api/children/[id]/location/current/route';
import { GET as history } from '@/app/api/children/[id]/location/history/route';

beforeAll(async () => { await resetDb(); });
beforeEach(() => setSender({ async send() { return true; } }));

describe('location reads', () => {
  it('returns current and history for an owned child', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    const { token } = await seedDevice(c.id);
    const base = new Date(); base.setUTCHours(8, 0, 0, 0);
    const at = (min: number) => new Date(base.getTime() + min * 60_000).toISOString();
    const day = base.toISOString().slice(0, 10);
    await upload(new Request('http://t/', {
      method: 'POST', headers: { authorization: `Bearer ${token}` },
      body: JSON.stringify({ points: [
        { lat: 32.07, lng: 34.78, recorded_at: at(0) },
        { lat: 32.08, lng: 34.79, recorded_at: at(5) },
      ] }),
    }));
    const ptok = await signAccess(p.id);
    const ctx = { params: Promise.resolve({ id: c.id }) };

    const cur = await current(new Request('http://t/', { headers: { authorization: `Bearer ${ptok}` } }), ctx);
    const curBody = await cur.json();
    expect(curBody.lng).toBeCloseTo(34.79, 5);

    const his = await history(new Request(`http://t/?date=${day}`, { headers: { authorization: `Bearer ${ptok}` } }), ctx);
    expect((await his.json()).points).toHaveLength(2);
  });

  it('forbids reading a child you do not own', async () => {
    const p1 = await seedParent(); const p2 = await seedParent();
    const c = await seedChild(p1.id);
    const ptok = await signAccess(p2.id);
    const r = await current(new Request('http://t/', { headers: { authorization: `Bearer ${ptok}` } }),
      { params: Promise.resolve({ id: c.id }) });
    expect(r.status).toBe(404);
  });
});
