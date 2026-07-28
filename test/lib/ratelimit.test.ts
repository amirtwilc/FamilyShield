import { beforeAll, describe, it, expect } from 'vitest';
import { memoryLimiter, databaseLimiter, clientKey } from '@/lib/ratelimit';
import { resetDb } from '../helpers/db';

describe('rate limiter', () => {
  beforeAll(async () => { await resetDb(); });
  it('allows up to max then blocks within window', () => {
    const rl = memoryLimiter(2, 1000);
    expect(rl.check('k').allowed).toBe(true);
    expect(rl.check('k').allowed).toBe(true);
    expect(rl.check('k').allowed).toBe(false);
    expect(rl.check('other').allowed).toBe(true);
  });
  it('derives a key from headers', () => {
    const req = new Request('http://t/', { headers: { 'x-forwarded-for': '1.2.3.4' } });
    expect(clientKey(req, 'login')).toBe('login:1.2.3.4');
  });
  it('shares counters through the database', async () => {
    const firstInstance = databaseLimiter(2, 60_000);
    const secondInstance = databaseLimiter(2, 60_000);
    expect((await firstInstance.check('shared')).allowed).toBe(true);
    expect((await secondInstance.check('shared')).allowed).toBe(true);
    expect((await firstInstance.check('shared')).allowed).toBe(false);
  });
});
