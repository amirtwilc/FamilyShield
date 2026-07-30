import { sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { decodeCursor, encodeCursor } from '@/lib/pagination';
import { decryptMessageRow } from '@/lib/messages/crypto';

const PAGE = 50;
type MessageRow = { id: string; body: unknown; created_at: string | Date; [key: string]: unknown };

/**
 * Shared chat paging for the parent and kid message routes.
 * - `?after=<iso>`  → newer-than messages, ascending (polling delta), no cursor.
 * - `?before=<cur>` → page of older history (keyset on created_at,id), ascending.
 * - neither         → newest page, ascending, with `nextCursor` to load older.
 */
export async function pageMessages(childId: string, url: URL, parentId?: string) {
  const afterRaw = url.searchParams.get('after');
  const afterCursor = afterRaw ? decodeCursor(afterRaw) : null;
  const legacyAfter = afterRaw && !afterCursor && !Number.isNaN(Date.parse(afterRaw)) ? afterRaw : null;
  if (afterCursor || legacyAfter) {
    const r = await db.execute(sql`
      SELECT id, sender, body, priority, created_at, read_at FROM messages
      WHERE child_id = ${childId}
        ${parentId ? sql`AND parent_id = ${parentId}` : sql``}
        ${afterCursor
          ? sql`AND (created_at, id) > (${afterCursor.recordedAt}, ${afterCursor.id})`
          : sql`AND created_at > ${legacyAfter}`}
      ORDER BY created_at ASC, id ASC LIMIT 500`);
    const messages = r.rows.map((row) => decryptMessageRow(row as MessageRow));
    return {
      messages,
      nextCursor: null as string | null,
      latestCursor: cursorForLast(messages),
    };
  }

  const beforeRaw = url.searchParams.get('before');
  const before = beforeRaw ? decodeCursor(beforeRaw) : null;
  const r = await db.execute(sql`
    SELECT id, sender, body, priority, created_at, read_at FROM messages
    WHERE child_id = ${childId}
      ${parentId ? sql`AND parent_id = ${parentId}` : sql``}
      ${before ? sql`AND (created_at, id) < (${before.recordedAt}, ${before.id})` : sql``}
    ORDER BY created_at DESC, id DESC LIMIT ${PAGE + 1}`);
  const rows = r.rows as MessageRow[];
  const hasMore = rows.length > PAGE;
  const page = rows.slice(0, PAGE).reverse().map(decryptMessageRow);
  const nextCursor = hasMore && page.length
    ? encodeCursor({ recordedAt: new Date(page[0].created_at).toISOString(), id: page[0].id })
    : null;
  return { messages: page, nextCursor, latestCursor: cursorForLast(page) };
}

function cursorForLast(rows: Array<{ id: unknown; created_at: unknown }>): string | null {
  const last = rows.at(-1);
  if (!last) return null;
  return encodeCursor({
    recordedAt: new Date(last.created_at as string | Date).toISOString(),
    id: String(last.id),
  });
}
