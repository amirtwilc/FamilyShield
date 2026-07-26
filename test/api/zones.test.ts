import { describe, it, expect, beforeAll } from 'vitest';
import { resetDb } from '../helpers/db';
import { seedParent, seedChild } from '../helpers/factories';
import { signAccess } from '@/lib/auth/jwt';
import { POST as createZone, GET as listZones } from '@/app/api/children/[id]/zones/route';
import { DELETE as delZone, PATCH as patchZone } from '@/app/api/children/[id]/zones/[zoneId]/route';

beforeAll(async () => { await resetDb(); });

describe('safe zones', () => {
  it('creates shared parent zones, lists newest first, edits, and deletes from any owned child route', async () => {
    const p = await seedParent(); const c = await seedChild(p.id); const c2 = await seedChild(p.id);
    const ptok = await signAccess(p.id);
    const ctx = { params: Promise.resolve({ id: c.id }) };
    const r1 = await createZone(new Request('http://t/', {
      method: 'POST', headers: { authorization: `Bearer ${ptok}` },
      body: JSON.stringify({ name: 'Home', lat: 32.07, lng: 34.78, radiusM: 150 }),
    }), ctx);
    expect(r1.status).toBe(201);
    const zone = await r1.json();
    await createZone(new Request('http://t/', {
      method: 'POST', headers: { authorization: `Bearer ${ptok}` },
      body: JSON.stringify({ name: 'School', lat: 32.08, lng: 34.79, radiusM: 300 }),
    }), ctx);

    const r2 = await listZones(new Request('http://t/', { headers: { authorization: `Bearer ${ptok}` } }),
      { params: Promise.resolve({ id: c2.id }) });
    const listed = (await r2.json()).zones;
    expect(listed).toHaveLength(2);
    expect(listed[0].name).toBe('School');
    expect(listed[1].name).toBe('Home');
    expect(listed[1].active).toBe(true);

    const rPatch = await patchZone(new Request('http://t/', {
      method: 'PATCH', headers: { authorization: `Bearer ${ptok}` },
      body: JSON.stringify({ name: 'Quiet home', radiusM: 200, active: false }),
    }), { params: Promise.resolve({ id: c2.id, zoneId: zone.id }) });
    expect(rPatch.status).toBe(200);
    const patched = await rPatch.json();
    expect(patched.name).toBe('Quiet home');
    expect(patched.radius_m).toBe(200);
    expect(patched.active).toBe(false);

    const r3 = await delZone(new Request('http://t/', { method: 'DELETE', headers: { authorization: `Bearer ${ptok}` } }),
      { params: Promise.resolve({ id: c2.id, zoneId: zone.id }) });
    expect(r3.status).toBe(200);
  });

  it('rejects out-of-spec radius values', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    const ptok = await signAccess(p.id);
    const ctx = { params: Promise.resolve({ id: c.id }) };
    const tooSmall = await createZone(new Request('http://t/', {
      method: 'POST', headers: { authorization: `Bearer ${ptok}` },
      body: JSON.stringify({ name: 'Tiny', lat: 32.07, lng: 34.78, radiusM: 40 }),
    }), ctx);
    expect(tooSmall.status).toBe(400);

    const notStep = await createZone(new Request('http://t/', {
      method: 'POST', headers: { authorization: `Bearer ${ptok}` },
      body: JSON.stringify({ name: 'Odd', lat: 32.07, lng: 34.78, radiusM: 155 }),
    }), ctx);
    expect(notStep.status).toBe(400);
  });
});
