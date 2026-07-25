import { and, eq, isNotNull, isNull } from 'drizzle-orm';
import { db } from '@/db/client';
import { children, devices, parents } from '@/db/schema';
import { getSender } from '@/lib/alerts/fcm';

const CHAT_MESSAGE_TYPE = 'chat_message';
const MAX_NOTIFICATION_BODY = 120;

function notificationBody(body: string): string {
  const trimmed = body.trim().replace(/\s+/g, ' ');
  if (trimmed.length <= MAX_NOTIFICATION_BODY) return trimmed;
  return `${trimmed.slice(0, MAX_NOTIFICATION_BODY - 3)}...`;
}

async function sendSafely(token: string, title: string, body: string, data: Record<string, string>): Promise<boolean> {
  try {
    return await getSender().send(token, title, notificationBody(body), data);
  } catch {
    return false;
  }
}

export async function notifyChildMessageFromParent(
  childId: string,
  parentId: string,
  messageId: string,
  body: string,
): Promise<boolean> {
  const [child] = await db.select({ name: children.displayName }).from(children).where(eq(children.id, childId));
  const tokens = await db.select({ token: devices.fcmToken })
    .from(devices)
    .where(and(eq(devices.childId, childId), isNull(devices.revokedAt), isNotNull(devices.fcmToken)));

  let sent = false;
  for (const row of tokens) {
    if (!row.token) continue;
    sent = await sendSafely(row.token, 'New message from parent', body, {
      type: CHAT_MESSAGE_TYPE,
      childId,
      parentId,
      messageId,
      recipient: 'child',
      childName: child?.name ?? '',
    }) || sent;
  }
  return sent;
}

export async function notifyParentMessageFromChild(
  childId: string,
  parentId: string,
  messageId: string,
  body: string,
): Promise<boolean> {
  const [row] = await db.select({
    childName: children.displayName,
    parentToken: parents.fcmToken,
  }).from(children)
    .innerJoin(parents, eq(parents.id, parentId))
    .where(eq(children.id, childId));

  if (!row?.parentToken) return false;
  return sendSafely(row.parentToken, `New message from ${row.childName}`, body, {
    type: CHAT_MESSAGE_TYPE,
    childId,
    parentId,
    messageId,
    recipient: 'parent',
  });
}
