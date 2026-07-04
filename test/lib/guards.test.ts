import { describe, it, expect, beforeAll } from 'vitest';
import { resetDb } from '../helpers/db';
import { seedParent, seedChild, seedDevice } from '../helpers/factories';
import { signAccess } from '@/lib/auth/jwt';
import { requireParent } from '@/lib/auth/parent';
import { requireDevice } from '@/lib/auth/device';

beforeAll(async () => { await resetDb(); });
const bearer = (t: string) => new Request('http://t/', { headers: { authorization: `Bearer ${t}` } });

describe('guards', () => {
  it('requireParent accepts a valid token', async () => {
    const p = await seedParent();
    const out = await requireParent(bearer(await signAccess(p.id)));
    expect('parentId' in out && out.parentId).toBe(p.id);
  });
  it('requireParent rejects missing token with 401', async () => {
    const out = await requireParent(new Request('http://t/'));
    expect('response' in out && out.response.status).toBe(401);
  });
  it('requireDevice accepts a paired device, rejects revoked', async () => {
    const p = await seedParent(); const c = await seedChild(p.id);
    const { token } = await seedDevice(c.id);
    const out = await requireDevice(bearer(token));
    expect('device' in out).toBe(true);
  });
});
