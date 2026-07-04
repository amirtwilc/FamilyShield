import { sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { requireParent } from '@/lib/auth/parent';
import { assertChildOwned } from '@/lib/ownership';
import { parseBody } from '@/lib/validate';
import { ok, err } from '@/lib/http';
import { sendMessageSchema } from '@/lib/schemas/messages';
import { pageMessages } from '@/lib/messages';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string }> };

/** Chat history for a child. `?after=<iso>` returns newer messages (polling
 *  delta); `?before=<cursor>` pages older history (keyset); `?markRead=1` marks
 *  the child's messages read. Newest page returns a `nextCursor` for older. */
export async function GET(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const url = new URL(req.url);
  if (url.searchParams.get('markRead') === '1') {
    await db.execute(sql`UPDATE messages SET read_at = now() WHERE child_id = ${id} AND parent_id = ${a.parentId} AND sender = 'child' AND read_at IS NULL`);
  }
  return ok(await pageMessages(id, url, a.parentId));
}

export async function POST(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const p = await parseBody(req, sendMessageSchema); if ('response' in p) return p.response;
  const r = await db.execute(sql`
    INSERT INTO messages (child_id, parent_id, sender, body) VALUES (${id}, ${a.parentId}, 'parent', ${p.data.body})
    RETURNING id, sender, body, created_at, read_at`);
  return ok(r.rows[0], 201);
}
