import { describe, it, expect, beforeAll } from 'vitest';
import { resetDb } from '../helpers/db';
import { seedParent, seedChild, seedDevice } from '../helpers/factories';
import { db } from '@/db/client';
import { alerts, devices } from '@/db/schema';
import { eq } from 'drizzle-orm';
import { signAccess } from '@/lib/auth/jwt';
import { GET as listAlerts } from '@/app/api/children/[id]/alerts/route';
import { DELETE as revoke } from '@/app/api/devices/[id]/route';

beforeAll(async () => { await resetDb(); });

describe('alerts + revoke', () => {
  it('lists alerts for an owned child', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    await db.insert(alerts).values({ childId: c.id, type: 'offline', payload: {} });
    const ptok = await signAccess(p.id);
    const r = await listAlerts(new Request('http://t/', { headers: { authorization: `Bearer ${ptok}` } }),
      { params: Promise.resolve({ id: c.id }) });
    expect((await r.json()).alerts).toHaveLength(1);
  });

  it('revokes an owned device', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    const { device } = await seedDevice(c.id);
    const ptok = await signAccess(p.id);
    const r = await revoke(new Request('http://t/', { method: 'DELETE', headers: { authorization: `Bearer ${ptok}` } }),
      { params: Promise.resolve({ id: device.id }) });
    expect(r.status).toBe(200);
    const [d] = await db.select().from(devices).where(eq(devices.id, device.id));
    expect(d.revokedAt).toBeTruthy();
  });

  it('denies alerts access to unowned child', async () => {
    const p1 = await seedParent(); const p2 = await seedParent();
    const c = await seedChild(p1.id);
    await db.insert(alerts).values({ childId: c.id, type: 'offline', payload: {} });
    const p2tok = await signAccess(p2.id);
    const r = await listAlerts(new Request('http://t/', { headers: { authorization: `Bearer ${p2tok}` } }),
      { params: Promise.resolve({ id: c.id }) });
    expect(r.status).toBe(404);
  });
});
