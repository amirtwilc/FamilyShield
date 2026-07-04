import { err } from './http';

export interface RateLimiter { check(key: string): { allowed: boolean }; }

// In-memory limiter is per-instance; for multi-instance scale swap `memoryLimiter`
// for an Upstash/Redis-backed implementation behind the same `RateLimiter` interface.
// The interface boundary keeps this a drop-in change.
export function memoryLimiter(max: number, windowMs: number): RateLimiter {
  const hits = new Map<string, { count: number; reset: number }>();
  return {
    check(key) {
      const now = Date.now();
      const e = hits.get(key);
      if (!e || e.reset < now) { hits.set(key, { count: 1, reset: now + windowMs }); return { allowed: true }; }
      e.count++;
      return { allowed: e.count <= max };
    },
  };
}

export function clientKey(req: Request, suffix: string): string {
  const ip = (req.headers.get('x-forwarded-for') ?? 'unknown').split(',')[0]!.trim();
  return `${suffix}:${ip}`;
}

export const tooMany = () => err('rate_limited', 'Too many requests, slow down', 429);
