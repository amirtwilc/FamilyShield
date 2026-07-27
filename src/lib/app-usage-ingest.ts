import { sql } from 'drizzle-orm';
import { db } from '@/db/client';
import type { devices } from '@/db/schema';
import { fireAppUsageLimitAlertsForDay } from '@/lib/alerts/engine';

const MIN_REPORTED_MINUTES = 5;

type Device = typeof devices.$inferSelect;
type UsageItem = {
  app: string;
  package_name?: string;
  category: string;
  minutes: number;
  day?: string;
  is_relevant?: boolean;
};

async function currentDbDay(): Promise<string> {
  const r = await db.execute(sql`SELECT to_char(CURRENT_DATE, 'YYYY-MM-DD') AS day`);
  return (r.rows[0] as { day: string }).day;
}

export async function upsertAppUsageReport(
  device: Device,
  usageItems: UsageItem[],
  options: { touchLastSeen?: boolean } = {},
): Promise<{ inserted: number }> {
  const today = await currentDbDay();
  const byKey = new Map<string, UsageItem & { normalizedDay: string }>();
  for (const it of usageItems) {
    if ((it.is_relevant ?? true) !== true || it.minutes < MIN_REPORTED_MINUTES) continue;
    const normalizedDay = it.day ?? today;
    byKey.set(`${it.package_name ?? it.app}|${normalizedDay}`, { ...it, normalizedDay });
  }
  const items = [...byKey.values()];
  if (items.length === 0) {
    if (options.touchLastSeen) {
      await db.execute(sql`UPDATE devices SET last_seen_at = now() WHERE id = ${device.id}`);
    }
    return { inserted: 0 };
  }

  const rows = items.map((it) => sql`(
    ${device.childId},
    ${it.package_name ?? it.app},
    ${it.app},
    ${it.category},
    ${it.minutes},
    ${it.normalizedDay}::date,
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
  if (options.touchLastSeen) {
    await db.execute(sql`UPDATE devices SET last_seen_at = now() WHERE id = ${device.id}`);
  }
  if (items.some((it) => it.normalizedDay === today)) {
    await fireAppUsageLimitAlertsForDay(device.childId, today);
  }
  return { inserted: items.length };
}
