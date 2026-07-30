import { sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { requireDevice } from '@/lib/auth/device';
import { parseBody } from '@/lib/validate';
import { ok } from '@/lib/http';
import { sendMessageSchema } from '@/lib/schemas/messages';
import { pageMessages } from '@/lib/messages';
import { firstMonitorParentId } from '@/lib/monitoring';
import { notifyParentMessageFromChild } from '@/lib/messages/notifications';
import { encryptMessageBody, decryptMessageRow } from '@/lib/messages/crypto';
import { enforceChatSendLimit } from '@/lib/messages/limits';

export const runtime = 'nodejs';

/** Kid-device view of the chat. Opening it marks the parent's messages read.
 *  Supports the same after/before paging as the parent route. */
export async function GET(req: Request) {
  const a = await requireDevice(req); if ('response' in a) return a.response;
  const childId = a.device.childId;
  const parentId = await firstMonitorParentId(childId);
  if (!parentId) return ok({ messages: [], nextCursor: null });
  await db.execute(sql`UPDATE messages SET read_at = now() WHERE child_id = ${childId} AND parent_id = ${parentId} AND sender = 'parent' AND read_at IS NULL`);
  return ok(await pageMessages(childId, new URL(req.url), parentId));
}

export async function POST(req: Request) {
  const a = await requireDevice(req); if ('response' in a) return a.response;
  const parentId = await firstMonitorParentId(a.device.childId);
  if (!parentId) return ok({ messages: [], nextCursor: null });
  const limited = await enforceChatSendLimit(`device:${a.device.id}`);
  if (limited) return limited;
  const p = await parseBody(req, sendMessageSchema); if ('response' in p) return p.response;
  const encryptedBody = encryptMessageBody(p.data.body);
  const r = await db.execute(sql`
    INSERT INTO messages (child_id, parent_id, sender, body) VALUES (${a.device.childId}, ${parentId}, 'child', ${encryptedBody})
    RETURNING id, sender, body, priority, created_at, read_at`);
  const message = r.rows[0] as { id: string };
  await notifyParentMessageFromChild(a.device.childId, parentId, message.id, p.data.body);
  await db.execute(sql`UPDATE devices SET last_seen_at = now() WHERE id = ${a.device.id}`);
  return ok(decryptMessageRow(r.rows[0] as { body: unknown }), 201);
}
