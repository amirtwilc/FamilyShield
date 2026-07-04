import { describe, it, expect } from 'vitest';
import { memoryLimiter, clientKey } from '@/lib/ratelimit';

describe('rate limiter', () => {
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
});
