import { and, eq, sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { appUsageLimits, devices } from '@/db/schema';
import { requireParent } from '@/lib/auth/parent';
import { averageUsageMinutes, hasYesterdayUsageData } from '@/lib/app-usage-summary';
import { assertChildOwned } from '@/lib/ownership';
import { ok, err } from '@/lib/http';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string }> };

const DOW = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MIN_VISIBLE_MINUTES = 5;
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

function selectedDay(req: Request): string | null {
  const date = new URL(req.url).searchParams.get('date');
  if (!date || !DATE_RE.test(date)) return null;
  const parsed = new Date(date + 'T00:00:00Z');
  if (Number.isNaN(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== date) return null;
  return date;
}

/** Screen-time summary for a child: today's total + per-app breakdown, the last
 *  7 days of daily totals (for the trend chart), and yesterday's total. */
export async function GET(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const day = selectedDay(req);

  const today = await db.execute(sql`
    SELECT COALESCE(SUM(minutes),0)::int AS m
    FROM app_usage
    WHERE child_id=${id} AND day = COALESCE(${day}::date, CURRENT_DATE) AND is_relevant = true AND minutes >= ${MIN_VISIBLE_MINUTES}`);
  const yest = await db.execute(sql`
    SELECT COALESCE(SUM(minutes),0)::int AS m, COUNT(*)::int AS n
    FROM app_usage
    WHERE child_id=${id} AND day = COALESCE(${day}::date, CURRENT_DATE) - 1 AND is_relevant = true AND minutes >= ${MIN_VISIBLE_MINUTES}`);
  const appsR = await db.execute(sql`
    SELECT package_name AS "packageName", app, category, SUM(minutes)::int AS min
    FROM app_usage
    WHERE child_id=${id} AND day = COALESCE(${day}::date, CURRENT_DATE) AND is_relevant = true AND minutes >= ${MIN_VISIBLE_MINUTES}
    GROUP BY package_name, app, category
    ORDER BY min DESC`);
  const updatedR = await db.execute(sql`
    SELECT MAX(last_reported_at) AS "lastUpdatedAt"
    FROM app_usage
    WHERE child_id=${id} AND day = COALESCE(${day}::date, CURRENT_DATE) AND is_relevant = true AND minutes >= ${MIN_VISIBLE_MINUTES}`);
  const accessR = await db.execute(sql`
    SELECT
      bool_or(${devices.appUsageAccessGranted}) FILTER (WHERE ${devices.appUsageAccessGranted} IS NOT NULL) AS granted
    FROM ${devices}
    WHERE ${devices.childId} = ${id} AND ${devices.revokedAt} IS NULL`);
  const limitsR = await db.select().from(appUsageLimits).where(and(
    eq(appUsageLimits.parentId, a.parentId),
    eq(appUsageLimits.childId, id),
  ));
  const weekR = await db.execute(sql`
    SELECT
      to_char(d.day::date, 'YYYY-MM-DD') AS day,
      COALESCE(SUM(u.minutes),0)::int AS min,
      COUNT(u.child_id)::int AS "dataPoints"
    FROM generate_series(CURRENT_DATE - 6, CURRENT_DATE, interval '1 day') AS d(day)
    LEFT JOIN app_usage u ON u.child_id=${id} AND u.day = d.day::date AND u.is_relevant = true AND u.minutes >= ${MIN_VISIBLE_MINUTES}
    GROUP BY d.day ORDER BY d.day ASC`);
  const previousTotalsR = await db.execute(sql`
    SELECT
      to_char(d.day::date, 'YYYY-MM-DD') AS day,
      COALESCE(SUM(u.minutes),0)::int AS min,
      COUNT(u.child_id)::int AS "dataPoints"
    FROM generate_series(CURRENT_DATE - 7, CURRENT_DATE, interval '1 day') AS d(day)
    LEFT JOIN app_usage u ON u.child_id=${id} AND u.day = d.day::date AND u.is_relevant = true AND u.minutes >= ${MIN_VISIBLE_MINUTES}
    GROUP BY d.day ORDER BY d.day ASC`);
  const weekAppsR = await db.execute(sql`
    SELECT
      to_char(day, 'YYYY-MM-DD') AS day,
      package_name AS "packageName",
      app,
      category,
      SUM(minutes)::int AS min,
      MAX(last_reported_at) AS "lastUpdatedAt"
    FROM app_usage
    WHERE child_id=${id}
      AND day BETWEEN CURRENT_DATE - 6 AND CURRENT_DATE
      AND is_relevant = true
      AND minutes >= ${MIN_VISIBLE_MINUTES}
    GROUP BY day, package_name, app, category
    ORDER BY day ASC, min DESC`);

  const totalTodayMin = (today.rows[0] as { m: number }).m;
  const yesterday = yest.rows[0] as { m: number; n: number };
  const yesterdayMin = yesterday.m;
  const yesterdayHasData = hasYesterdayUsageData(yesterday.n);
  const week = (weekR.rows as { day: string; min: number; dataPoints: number }[]).map((r) => ({
    day: r.day, dow: DOW[new Date(r.day + 'T00:00:00Z').getUTCDay()], min: r.min, hasData: r.dataPoints > 0,
  }));
  const totalsByDay = new Map(
    (previousTotalsR.rows as { day: string; min: number; dataPoints: number }[])
      .map((r) => [r.day, { min: r.min, hasData: r.dataPoints > 0 }] as const),
  );
  const appsByDay = new Map<string, { packageName: string; app: string; category: string; min: number }[]>();
  const updatedByDay = new Map<string, Date | string | null>();
  for (const row of weekAppsR.rows as { day: string; packageName: string; app: string; category: string; min: number; lastUpdatedAt: Date | string | null }[]) {
    const appsForDay = appsByDay.get(row.day) ?? [];
    appsForDay.push({ packageName: row.packageName, app: row.app, category: row.category, min: row.min });
    appsByDay.set(row.day, appsForDay);
    const previousUpdated = updatedByDay.get(row.day);
    if (!previousUpdated || (row.lastUpdatedAt && new Date(row.lastUpdatedAt) > new Date(previousUpdated))) {
      updatedByDay.set(row.day, row.lastUpdatedAt);
    }
  }
  const dayDetails = week.map((r) => {
    const previousDay = new Date(r.day + 'T00:00:00Z');
    previousDay.setUTCDate(previousDay.getUTCDate() - 1);
    const previous = totalsByDay.get(previousDay.toISOString().slice(0, 10));
    return {
      day: r.day,
      totalMin: r.min,
      previousMin: previous?.min ?? 0,
      previousHasData: previous?.hasData ?? false,
      apps: appsByDay.get(r.day) ?? [],
      lastUpdatedAt: updatedByDay.get(r.day) ?? null,
    };
  });
  const avgWeekMin = averageUsageMinutes(week);
  const apps = (appsR.rows as { packageName: string; app: string; category: string; min: number }[])
    .map((r) => ({ packageName: r.packageName, app: r.app, category: r.category, min: r.min }));
  const lastUpdatedAt = (updatedR.rows[0] as { lastUpdatedAt: Date | string | null }).lastUpdatedAt;
  const appUsageAccessGranted = (accessR.rows[0] as { granted: boolean | null }).granted;
  return ok({
    selectedDay: day,
    totalTodayMin,
    yesterdayMin,
    yesterdayHasData,
    avgWeekMin,
    week,
    dayDetails,
    apps,
    limits: limitsR.map((limit) => ({
      id: limit.id,
      childId: limit.childId,
      type: limit.type,
      packageName: limit.packageName,
      app: limit.app,
      category: limit.category,
      limitMinutes: limit.limitMinutes,
      active: limit.active,
      createdAt: limit.createdAt,
      updatedAt: limit.updatedAt,
    })),
    lastUpdatedAt,
    appUsageAccessGranted,
  });
}
