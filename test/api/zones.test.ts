import { describe, it, expect, beforeAll } from 'vitest';
import { resetDb } from '../helpers/db';
import { seedParent, seedChild } from '../helpers/factories';
import { signAccess } from '@/lib/auth/jwt';
import { POST as createZone, GET as listZones } from '@/app/api/children/[id]/zones/route';
import { DELETE as delZone } from '@/app/api/children/[id]/zones/[zoneId]/route';

beforeAll(async () => { await resetDb(); });

describe('safe zones', () => {
  it('creates, lists, deletes a zone', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    const ptok = await signAccess(p.id);
    const ctx = { params: Promise.resolve({ id: c.id }) };
    const r1 = await createZone(new Request('http://t/', {
      method: 'POST', headers: { authorization: `Bearer ${ptok}` },
      body: JSON.stringify({ name: 'Home', lat: 32.07, lng: 34.78, radiusM: 150 }),
    }), ctx);
    expect(r1.status).toBe(201);
    const zone = await r1.json();

    const r2 = await listZones(new Request('http://t/', { headers: { authorization: `Bearer ${ptok}` } }), ctx);
    expect((await r2.json()).zones).toHaveLength(1);

    const r3 = await delZone(new Request('http://t/', { method: 'DELETE', headers: { authorization: `Bearer ${ptok}` } }),
      { params: Promise.resolve({ id: c.id, zoneId: zone.id }) });
    expect(r3.status).toBe(200);
  });
});
