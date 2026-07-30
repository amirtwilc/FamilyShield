import { sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { requireParent } from '@/lib/auth/parent';
import { assertChildOwned } from '@/lib/ownership';
import { parseBody } from '@/lib/validate';
import { ok, err } from '@/lib/http';
import { sendMessageSchema } from '@/lib/schemas/messages';
import { pageMessages } from '@/lib/messages';
import { notifyChildMessageFromParent } from '@/lib/messages/notifications';
import { encryptMessageBody, decryptMessageRow } from '@/lib/messages/crypto';
import { enforceChatSendLimit } from '@/lib/messages/limits';

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
  const limited = await enforceChatSendLimit(`parent:${a.parentId}`);
  if (limited) return limited;
  const p = await parseBody(req, sendMessageSchema); if ('response' in p) return p.response;
  const encryptedBody = encryptMessageBody(p.data.body);
  const r = await db.execute(sql`
    INSERT INTO messages (child_id, parent_id, sender, body) VALUES (${id}, ${a.parentId}, 'parent', ${encryptedBody})
    RETURNING id, sender, body, priority, created_at, read_at`);
  const message = r.rows[0] as { id: string };
  await notifyChildMessageFromParent(id, a.parentId, message.id, p.data.body);
  return ok(decryptMessageRow(r.rows[0] as { body: unknown }), 201);
}
