import { ok } from '@/lib/http';

export const runtime = 'nodejs';

/** Lightweight liveness probe for the platform health check (no DB hit). */
export function GET() {
  return ok({ ok: true });
}
