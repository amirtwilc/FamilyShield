import { createHash } from 'node:crypto';
import { sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { err } from './http';

type RateLimitResult = { allowed: boolean };
export interface SyncRateLimiter {
  check(key: string): RateLimitResult;
}
export interface AsyncRateLimiter {
  check(key: string): Promise<RateLimitResult>;
}

// The in-memory implementation is retained for local/synchronous callers. Public
// API routes use the database-backed limiter below so limits span all instances.
export function memoryLimiter(max: number, windowMs: number): SyncRateLimiter {
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

/**
 * A deployment-wide fixed-window limiter backed by Postgres. Keys are hashed so
 * raw client IPs are not retained. This remains effective across serverless
 * instances, unlike an in-memory map.
 */
export function databaseLimiter(max: number, windowMs: number): AsyncRateLimiter {
  return {
    async check(key) {
      const keyHash = createHash('sha256').update(key).digest('hex');
      const result = await db.execute(sql`
        INSERT INTO rate_limit_buckets (key_hash, count, window_started_at, expires_at)
        VALUES (
          ${keyHash},
          1,
          now(),
          now() + (${windowMs} * interval '1 millisecond')
        )
        ON CONFLICT (key_hash) DO UPDATE SET
          count = CASE
            WHEN rate_limit_buckets.expires_at <= now() THEN 1
            ELSE rate_limit_buckets.count + 1
          END,
          window_started_at = CASE
            WHEN rate_limit_buckets.expires_at <= now() THEN now()
            ELSE rate_limit_buckets.window_started_at
          END,
          expires_at = CASE
            WHEN rate_limit_buckets.expires_at <= now()
              THEN now() + (${windowMs} * interval '1 millisecond')
            ELSE rate_limit_buckets.expires_at
          END
        RETURNING count`);
      const count = Number((result.rows[0] as { count: number }).count);

      // Cheap probabilistic cleanup prevents unbounded growth without adding a
      // cleanup query to every authentication attempt.
      if (count === 1 && keyHash.startsWith('00')) {
        await db.execute(sql`
          DELETE FROM rate_limit_buckets
          WHERE expires_at < now() - interval '1 day'`);
      }
      return { allowed: count <= max };
    },
  };
}

export function clientKey(req: Request, suffix: string): string {
  const forwarded = req.headers.get('x-vercel-forwarded-for')
    ?? req.headers.get('x-forwarded-for')
    ?? req.headers.get('x-real-ip')
    ?? 'unknown';
  const ip = forwarded.split(',')[0]!.trim();
  return `${suffix}:${ip}`;
}

export const tooMany = () => err('rate_limited', 'Too many requests, slow down', 429);
