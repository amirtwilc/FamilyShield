import { timingSafeEqual } from 'node:crypto';
import { err } from '@/lib/http';
import { requireEnv } from '@/lib/env';

function safeEqual(a: string, b: string): boolean {
  const ab = Buffer.from(a);
  const bb = Buffer.from(b);
  return ab.length === bb.length && timingSafeEqual(ab, bb);
}

export function requireCron(req: Request): Response | null {
  const auth = req.headers.get('authorization') ?? '';
  return safeEqual(auth, `Bearer ${requireEnv('CRON_SECRET')}`)
    ? null
    : err('unauthorized', 'Bad cron secret', 401);
}
