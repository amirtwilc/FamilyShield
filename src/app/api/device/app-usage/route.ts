import { sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { requireDevice } from '@/lib/auth/device';
import { parseBody } from '@/lib/validate';
import { ok } from '@/lib/http';
import { reportUsageSchema } from '@/lib/schemas/appusage';

export const runtime = 'nodejs';
const MIN_REPORTED_MINUTES = 5;

/** Kid device reports per-app screen time (from UsageStatsManager). Upserts one
 *  row per (child, app, day) so re-reporting a day overwrites rather than dupes. */
export async function POST(req: Request) {
  const a = await requireDevice(req); if ('response' in a) return a.response;
  const p = await parseBody(req, reportUsageSchema); if ('response' in p) return p.response;

  // De-dupe by (package, day) so one batch can't hit the same conflict target twice,
  // then upsert every row in a single statement (one round-trip, not N).
  const byKey = new Map<string, typeof p.data.items[number]>();
  for (const it of p.data.items) {
    if ((it.is_relevant ?? true) !== true || it.minutes < MIN_REPORTED_MINUTES) continue;
    byKey.set(`${it.package_name ?? it.app}|${it.day ?? ''}`, it);
  }
  const items = [...byKey.values()];
  if (items.length === 0) {
    await db.execute(sql`UPDATE devices SET last_seen_at = now() WHERE id = ${a.device.id}`);
    return ok({ inserted: 0 });
  }

  const rows = items.map((it) => sql`(
    ${a.device.childId},
    ${it.package_name ?? it.app},
    ${it.app},
    ${it.category},
    ${it.minutes},
    COALESCE(${it.day ?? null}::date, CURRENT_DATE),
    true,
    null,
    now()
  )`);
  await db.execute(sql`
    INSERT INTO app_usage (child_id, package_name, app, category, minutes, day, is_relevant, hidden_reason, last_reported_at)
    VALUES ${sql.join(rows, sql`, `)}
    ON CONFLICT (child_id, package_name, day) DO UPDATE SET
      app = EXCLUDED.app,
      minutes = EXCLUDED.minutes,
      category = EXCLUDED.category,
      is_relevant = true,
      hidden_reason = NULL,
      last_reported_at = EXCLUDED.last_reported_at`);
  await db.execute(sql`UPDATE devices SET last_seen_at = now() WHERE id = ${a.device.id}`);
  return ok({ inserted: items.length });
}
