import { afterEach, beforeAll, describe, it, expect } from 'vitest';
import { memoryLimiter, databaseLimiter, clientKey } from '@/lib/ratelimit';
import { resetDb } from '../helpers/db';

describe('rate limiter', () => {
  beforeAll(async () => { await resetDb(); });
  afterEach(() => {
    delete process.env.TRUST_PROXY;
    delete process.env.VERCEL;
  });
  it('allows up to max then blocks within window', () => {
    const rl = memoryLimiter(2, 1000);
    expect(rl.check('k').allowed).toBe(true);
    expect(rl.check('k').allowed).toBe(true);
    expect(rl.check('k').allowed).toBe(false);
    expect(rl.check('other').allowed).toBe(true);
  });
  it('ignores forwarding headers unless the proxy is trusted', () => {
    const req = new Request('http://t/', { headers: { 'x-forwarded-for': 'spoofed' } });
    expect(clientKey(req, 'login')).toBe('login:unknown');
  });
  it('uses the proxy-appended address rather than a spoofed leading value', () => {
    process.env.TRUST_PROXY = 'true';
    const req = new Request('http://t/', { headers: { 'x-forwarded-for': 'spoofed, 1.2.3.4' } });
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
