import { sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { requireParent } from '@/lib/auth/parent';
import { ok } from '@/lib/http';

export const runtime = 'nodejs';

/** Chat conversation list for the parent: each child's last message + unread
 *  count, in ONE query (a LATERAL for the last row + a partial-indexed unread
 *  count) instead of fetching every child's whole thread. */
export async function GET(req: Request) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const r = await db.execute(sql`
    SELECT c.id AS child_id,
      m.id AS last_id, m.sender AS last_sender, m.body AS last_body, m.created_at AS last_at,
      (SELECT count(*) FROM messages u
       WHERE u.child_id = c.id AND u.parent_id = ${a.parentId} AND u.sender = 'child' AND u.read_at IS NULL)::int AS unread
    FROM children c
    LEFT JOIN LATERAL (
      SELECT id, sender, body, created_at FROM messages mm
      WHERE mm.child_id = c.id AND mm.parent_id = ${a.parentId}
      ORDER BY mm.created_at DESC, mm.id DESC LIMIT 1
    ) m ON true
    INNER JOIN child_parent_links cpl ON cpl.child_id = c.id
    WHERE cpl.parent_id = ${a.parentId}`);

  const conversations = (r.rows as Array<{
    child_id: string; last_id: string | null; last_sender: string | null;
    last_body: string | null; last_at: string | Date | null; unread: number;
  }>).map((x) => ({
    childId: x.child_id,
    unread: x.unread,
    last: x.last_id
      ? { id: x.last_id, sender: x.last_sender, body: x.last_body, created_at: new Date(x.last_at as string | Date).toISOString() }
      : null,
  }));
  return ok({ conversations });
}
