import { sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { decodeCursor, encodeCursor } from '@/lib/pagination';

const PAGE = 50;

/**
 * Shared chat paging for the parent and kid message routes.
 * - `?after=<iso>`  → newer-than messages, ascending (polling delta), no cursor.
 * - `?before=<cur>` → page of older history (keyset on created_at,id), ascending.
 * - neither         → newest page, ascending, with `nextCursor` to load older.
 */
export async function pageMessages(childId: string, url: URL, parentId?: string) {
  const afterRaw = url.searchParams.get('after');
  const after = afterRaw && !Number.isNaN(Date.parse(afterRaw)) ? afterRaw : null;
  if (after) {
    const r = await db.execute(sql`
      SELECT id, sender, body, created_at, read_at FROM messages
      WHERE child_id = ${childId}
        ${parentId ? sql`AND parent_id = ${parentId}` : sql``}
        AND created_at > ${after}
      ORDER BY created_at ASC, id ASC LIMIT 500`);
    return { messages: r.rows, nextCursor: null as string | null };
  }

  const beforeRaw = url.searchParams.get('before');
  const before = beforeRaw ? decodeCursor(beforeRaw) : null;
  const r = await db.execute(sql`
    SELECT id, sender, body, created_at, read_at FROM messages
    WHERE child_id = ${childId}
      ${parentId ? sql`AND parent_id = ${parentId}` : sql``}
      ${before ? sql`AND (created_at, id) < (${before.recordedAt}, ${before.id})` : sql``}
    ORDER BY created_at DESC, id DESC LIMIT ${PAGE + 1}`);
  const rows = r.rows as Array<{ id: string; created_at: string | Date }>;
  const hasMore = rows.length > PAGE;
  const page = rows.slice(0, PAGE).reverse();
  const nextCursor = hasMore && page.length
    ? encodeCursor({ recordedAt: new Date(page[0].created_at).toISOString(), id: page[0].id })
    : null;
  return { messages: page, nextCursor };
}
