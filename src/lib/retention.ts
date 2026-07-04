import { sql } from 'drizzle-orm';
import { db } from '@/db/client';

export const FREE_TIER_CODE = 'free';
export const FREE_LOCATION_RETENTION_DAYS = 2;
export const FREE_MAX_CHILDREN = 5;

export async function effectiveRetentionDays(childId: string): Promise<number> {
  const r = await db.execute(sql`
    SELECT COALESCE(MAX(st.location_retention_days), ${FREE_LOCATION_RETENTION_DAYS})::int AS days
    FROM child_parent_links cpl
    JOIN parents p ON p.id = cpl.parent_id
    JOIN subscription_tiers st ON st.code = p.tier_code
    WHERE cpl.child_id = ${childId}`);
  return Number((r.rows[0] as any)?.days ?? FREE_LOCATION_RETENTION_DAYS);
}

export async function retentionCutoffForChild(childId: string, now = new Date()): Promise<Date> {
  const days = await effectiveRetentionDays(childId);
  return new Date(now.getTime() - days * 24 * 60 * 60 * 1000);
}

export async function parentTierLimits(parentId: string): Promise<{ maxChildren: number; tierCode: string }> {
  const r = await db.execute(sql`
    SELECT p.tier_code, st.max_children
    FROM parents p
    JOIN subscription_tiers st ON st.code = p.tier_code
    WHERE p.id = ${parentId}`);
  const row = r.rows[0] as any;
  return {
    tierCode: row?.tier_code ?? FREE_TIER_CODE,
    maxChildren: Number(row?.max_children ?? FREE_MAX_CHILDREN),
  };
}

export async function linkedChildCount(parentId: string): Promise<number> {
  const r = await db.execute(sql`
    SELECT count(*)::int AS count
    FROM child_parent_links
    WHERE parent_id = ${parentId}`);
  return Number((r.rows[0] as any)?.count ?? 0);
}

export async function enforceCanAddChild(parentId: string): Promise<
  { ok: true } | { ok: false; maxChildren: number; tierCode: string }
> {
  const [limits, count] = await Promise.all([parentTierLimits(parentId), linkedChildCount(parentId)]);
  return count < limits.maxChildren
    ? { ok: true }
    : { ok: false, maxChildren: limits.maxChildren, tierCode: limits.tierCode };
}

export async function sweepLocationRetention(now = new Date()): Promise<{
  deleted: number;
  clearedDevices: number;
  droppedPartitions: number;
}> {
  const deleted = await db.execute(sql`
    WITH child_cutoffs AS (
      SELECT cpl.child_id,
        ${now.toISOString()}::timestamptz - (MAX(st.location_retention_days)::text || ' days')::interval AS cutoff
      FROM child_parent_links cpl
      JOIN parents p ON p.id = cpl.parent_id
      JOIN subscription_tiers st ON st.code = p.tier_code
      GROUP BY cpl.child_id
    )
    DELETE FROM locations l
    USING devices d, child_cutoffs cc
    WHERE l.device_id = d.id
      AND d.child_id = cc.child_id
      AND l.recorded_at < cc.cutoff`);

  const cleared = await db.execute(sql`
    WITH child_cutoffs AS (
      SELECT cpl.child_id,
        ${now.toISOString()}::timestamptz - (MAX(st.location_retention_days)::text || ' days')::interval AS cutoff
      FROM child_parent_links cpl
      JOIN parents p ON p.id = cpl.parent_id
      JOIN subscription_tiers st ON st.code = p.tier_code
      GROUP BY cpl.child_id
    )
    UPDATE devices d
    SET last_location = NULL, last_location_at = NULL
    FROM child_cutoffs cc
    WHERE d.child_id = cc.child_id
      AND d.last_location_at IS NOT NULL
      AND d.last_location_at < cc.cutoff`);

  const droppedPartitions = await dropEmptyExpiredLocationPartitions(now);
  return {
    deleted: deleted.rowCount ?? 0,
    clearedDevices: cleared.rowCount ?? 0,
    droppedPartitions,
  };
}

async function dropEmptyExpiredLocationPartitions(now: Date): Promise<number> {
  const minRetention = await db.execute(sql`
    SELECT COALESCE(MIN(location_retention_days), ${FREE_LOCATION_RETENTION_DAYS})::int AS days
    FROM subscription_tiers
    WHERE is_active`);
  const shortestDays = Number((minRetention.rows[0] as any)?.days ?? FREE_LOCATION_RETENTION_DAYS);
  const cutoff = new Date(now.getTime() - shortestDays * 24 * 60 * 60 * 1000);

  const partitions = await db.execute(sql`
    SELECT c.relname AS name,
      pg_get_expr(c.relpartbound, c.oid) AS bound
    FROM pg_class c
    JOIN pg_inherits i ON i.inhrelid = c.oid
    JOIN pg_class p ON p.oid = i.inhparent
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE p.relname = 'locations' AND n.nspname = 'public'`);

  let dropped = 0;
  for (const row of partitions.rows as Array<{ name: string; bound: string }>) {
    const end = /TO \('([^']+)'\)/.exec(row.bound)?.[1];
    if (!end || new Date(`${end}T00:00:00Z`) >= cutoff) continue;
    const count = await db.execute(sql.raw(`SELECT 1 FROM "${row.name}" LIMIT 1`));
    if (count.rows.length > 0) continue;
    await db.execute(sql.raw(`DROP TABLE IF EXISTS "${row.name}"`));
    dropped++;
  }
  return dropped;
}
