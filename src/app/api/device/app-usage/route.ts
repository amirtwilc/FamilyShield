import { sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { requireDevice } from '@/lib/auth/device';
import { parseBody } from '@/lib/validate';
import { ok } from '@/lib/http';
import { reportUsageSchema } from '@/lib/schemas/appusage';

export const runtime = 'nodejs';

/** Kid device reports per-app screen time (from UsageStatsManager). Upserts one
 *  row per (child, app, day) so re-reporting a day overwrites rather than dupes. */
export async function POST(req: Request) {
  const a = await requireDevice(req); if ('response' in a) return a.response;
  const p = await parseBody(req, reportUsageSchema); if ('response' in p) return p.response;

  // De-dupe by (app, day) so one batch can't hit the same conflict target twice,
  // then upsert every row in a single statement (one round-trip, not N).
  const byKey = new Map<string, typeof p.data.items[number]>();
  for (const it of p.data.items) byKey.set(`${it.app}|${it.day ?? ''}`, it);
  const items = [...byKey.values()];

  const rows = items.map((it) => sql`(${a.device.childId}, ${it.app}, ${it.category}, ${it.minutes}, COALESCE(${it.day ?? null}::date, CURRENT_DATE))`);
  await db.execute(sql`
    INSERT INTO app_usage (child_id, app, category, minutes, day)
    VALUES ${sql.join(rows, sql`, `)}
    ON CONFLICT (child_id, app, day) DO UPDATE SET minutes = EXCLUDED.minutes, category = EXCLUDED.category`);
  await db.execute(sql`UPDATE devices SET last_seen_at = now() WHERE id = ${a.device.id}`);
  return ok({ inserted: items.length });
}
