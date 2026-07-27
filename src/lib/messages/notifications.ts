import { and, eq, isNotNull, isNull } from 'drizzle-orm';
import { db } from '@/db/client';
import { childParentLinks, children, devices, parents } from '@/db/schema';
import { getSender, type PushOptions } from '@/lib/alerts/fcm';

const CHAT_MESSAGE_TYPE = 'chat_message';
const MAX_NOTIFICATION_BODY = 120;
const CHAT_PUSH_OPTIONS: PushOptions = {
  includeNotification: false,
  android: { priority: 'high' },
};

function notificationBody(body: string): string {
  const trimmed = body.trim().replace(/\s+/g, ' ');
  if (trimmed.length <= MAX_NOTIFICATION_BODY) return trimmed;
  return `${trimmed.slice(0, MAX_NOTIFICATION_BODY - 3)}...`;
}

function pushError(error: unknown): Record<string, string | undefined> {
  const maybe = error as { code?: unknown; message?: unknown };
  return {
    code: typeof maybe.code === 'string' ? maybe.code : undefined,
    message: error instanceof Error ? error.message : String(error),
  };
}

async function sendSafely(token: string, title: string, body: string, data: Record<string, string>): Promise<boolean> {
  try {
    const sent = await getSender().send(token, title, notificationBody(body), data, CHAT_PUSH_OPTIONS);
    if (!sent) {
      console.warn('[push] Chat notification was not sent', {
        type: data.type,
        recipient: data.recipient,
        childId: data.childId,
        parentId: data.parentId,
        messageId: data.messageId,
      });
    }
    return sent;
  } catch (error) {
    console.error('[push] Chat notification send failed', {
      type: data.type,
      recipient: data.recipient,
      childId: data.childId,
      parentId: data.parentId,
      messageId: data.messageId,
      error: pushError(error),
    });
    return false;
  }
}

export async function notifyChildMessageFromParent(
  childId: string,
  parentId: string,
  messageId: string,
  body: string,
): Promise<boolean> {
  const [link] = await db.select({
    childName: children.displayName,
    parentEmail: parents.email,
    parentDisplayName: childParentLinks.parentDisplayName,
  }).from(childParentLinks)
    .innerJoin(children, eq(children.id, childParentLinks.childId))
    .innerJoin(parents, eq(parents.id, childParentLinks.parentId))
    .where(and(eq(childParentLinks.childId, childId), eq(childParentLinks.parentId, parentId)));
  const parentName = link?.parentDisplayName?.trim() || link?.parentEmail || '';
  const tokens = await db.select({ token: devices.fcmToken })
    .from(devices)
    .where(and(eq(devices.childId, childId), isNull(devices.revokedAt), isNotNull(devices.fcmToken)));

  let sent = false;
  for (const row of tokens) {
    if (!row.token) continue;
    sent = await sendSafely(row.token, parentName ? `New message from ${parentName}` : 'New message from parent', body, {
      type: CHAT_MESSAGE_TYPE,
      childId,
      parentId,
      messageId,
      recipient: 'child',
      childName: link?.childName ?? '',
      parentName,
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
