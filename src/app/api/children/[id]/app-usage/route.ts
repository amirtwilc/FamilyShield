import { sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { devices } from '@/db/schema';
import { requireParent } from '@/lib/auth/parent';
import { assertChildOwned } from '@/lib/ownership';
import { ok, err } from '@/lib/http';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string }> };

const DOW = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

/** Screen-time summary for a child: today's total + per-app breakdown, the last
 *  7 days of daily totals (for the trend chart), and yesterday's total. */
export async function GET(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);

  const today = await db.execute(sql`SELECT COALESCE(SUM(minutes),0)::int AS m FROM app_usage WHERE child_id=${id} AND day = CURRENT_DATE`);
  const yest = await db.execute(sql`SELECT COALESCE(SUM(minutes),0)::int AS m FROM app_usage WHERE child_id=${id} AND day = CURRENT_DATE - 1`);
  const appsR = await db.execute(sql`
    SELECT app, category, SUM(minutes)::int AS min FROM app_usage
    WHERE child_id=${id} AND day = CURRENT_DATE GROUP BY app, category ORDER BY min DESC`);
  const accessR = await db.execute(sql`
    SELECT
      bool_or(${devices.appUsageAccessGranted}) FILTER (WHERE ${devices.appUsageAccessGranted} IS NOT NULL) AS granted
    FROM ${devices}
    WHERE ${devices.childId} = ${id} AND ${devices.revokedAt} IS NULL`);
  const weekR = await db.execute(sql`
    SELECT to_char(d.day::date, 'YYYY-MM-DD') AS day, COALESCE(SUM(u.minutes),0)::int AS min
    FROM generate_series(CURRENT_DATE - 6, CURRENT_DATE, interval '1 day') AS d(day)
    LEFT JOIN app_usage u ON u.child_id=${id} AND u.day = d.day::date
    GROUP BY d.day ORDER BY d.day ASC`);

  const totalTodayMin = (today.rows[0] as { m: number }).m;
  const yesterdayMin = (yest.rows[0] as { m: number }).m;
  const week = (weekR.rows as { day: string; min: number }[]).map((r) => ({
    day: r.day, dow: DOW[new Date(r.day + 'T00:00:00Z').getUTCDay()], min: r.min,
  }));
  const avgWeekMin = Math.round(week.reduce((s, w) => s + w.min, 0) / 7);
  const apps = (appsR.rows as { app: string; category: string; min: number }[])
    .map((r) => ({ app: r.app, category: r.category, min: r.min }));
  const appUsageAccessGranted = (accessR.rows[0] as { granted: boolean | null }).granted;
  return ok({ totalTodayMin, yesterdayMin, avgWeekMin, week, apps, appUsageAccessGranted });
}
