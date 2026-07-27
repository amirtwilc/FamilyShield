import { sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { requireParent } from '@/lib/auth/parent';
import { assertChildOwned } from '@/lib/ownership';
import { ok, err } from '@/lib/http';
import { retentionCutoffForChild } from '@/lib/retention';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string }> };

/** Number of recent calendar days exposed to the Android History day switcher.
 *  Keep this near the API because it defines the mobile/backend contract. */
export const HISTORY_DAY_RANGE_DAYS = 14;

export async function GET(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);

  const days = Math.min(
    Math.max(Number(new URL(req.url).searchParams.get('days') ?? HISTORY_DAY_RANGE_DAYS), 1),
    90,
  );
  const cutoff = await retentionCutoffForChild(id);
  const lookback = new Date(Date.now() - (days - 1) * 24 * 60 * 60 * 1000);
  const start = new Date(Math.max(startOfUtcDay(lookback).getTime(), cutoff.getTime())).toISOString();

  const r = await db.execute(sql`
    SELECT DISTINCT to_char(l.recorded_at AT TIME ZONE 'UTC', 'YYYY-MM-DD') AS day
    FROM locations l
    JOIN devices d ON d.id = l.device_id
    WHERE d.child_id = ${id}
      AND l.recorded_at >= ${start}
    ORDER BY day DESC`);

  return ok({ days: (r.rows as any[]).map((x) => x.day) });
}

function startOfUtcDay(date: Date): Date {
  return new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate()));
}
