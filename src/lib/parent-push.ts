import { and, eq } from 'drizzle-orm';
import { db } from '@/db/client';
import { childParentLinks, parentPushTokens } from '@/db/schema';

export async function registerParentPushToken(parentId: string, token: string): Promise<void> {
  await db.insert(parentPushTokens).values({ parentId, token }).onConflictDoUpdate({
    target: parentPushTokens.token,
    set: { parentId, updatedAt: new Date() },
  });
}

export async function unregisterParentPushToken(parentId: string, token: string): Promise<void> {
  await db.delete(parentPushTokens).where(and(
    eq(parentPushTokens.parentId, parentId),
    eq(parentPushTokens.token, token),
  ));
}

export async function parentPushTokenValues(parentId: string): Promise<string[]> {
  const rows = await db.select({ token: parentPushTokens.token })
    .from(parentPushTokens)
    .where(eq(parentPushTokens.parentId, parentId));
  return rows.map((row) => row.token);
}

export async function linkedParentIds(childId: string): Promise<string[]> {
  const rows = await db.select({ parentId: childParentLinks.parentId })
    .from(childParentLinks)
    .where(eq(childParentLinks.childId, childId));
  return rows.map((row) => row.parentId);
}

export function isPermanentPushTokenError(error: unknown): boolean {
  const code = typeof error === 'object' && error !== null && 'code' in error
    ? String((error as { code?: unknown }).code)
    : '';
  return code === 'messaging/registration-token-not-registered'
    || code === 'messaging/invalid-registration-token';
}

export async function pruneInvalidParentPushToken(token: string, error: unknown): Promise<void> {
  if (isPermanentPushTokenError(error)) {
    await db.delete(parentPushTokens).where(eq(parentPushTokens.token, token));
  }
}

export async function sendToParentInstallations(
  parentId: string,
  send: (token: string) => Promise<boolean>,
): Promise<boolean> {
  let delivered = false;
  for (const token of await parentPushTokenValues(parentId)) {
    try {
      delivered = await send(token) || delivered;
    } catch (error) {
      await pruneInvalidParentPushToken(token, error);
      console.error('[push] Parent notification send failed', {
        parentId,
        code: typeof error === 'object' && error !== null && 'code' in error
          ? String((error as { code?: unknown }).code)
          : undefined,
      });
    }
  }
  return delivered;
}

export async function sendToLinkedParentInstallations(
  childId: string,
  send: (parentId: string, token: string) => Promise<boolean>,
): Promise<boolean> {
  let delivered = false;
  for (const parentId of await linkedParentIds(childId)) {
    delivered = await sendToParentInstallations(parentId, (token) => send(parentId, token)) || delivered;
  }
  return delivered;
}
