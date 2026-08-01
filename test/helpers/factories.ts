import { db } from '@/db/client';
import { childParentLinks, children, devices, parentPushTokens, parents } from '@/db/schema';
import { hashToken } from '@/lib/auth/device';

export async function seedParent(email = `p${Date.now()}@t.io`) {
  const fcmToken = `fcm-${Date.now()}-${Math.random()}`;
  const [p] = await db.insert(parents)
    .values({ email, passwordHash: 'x' }).returning();
  await db.insert(parentPushTokens).values({ parentId: p.id, token: fcmToken });
  return { ...p, fcmToken };
}
export async function seedChild(parentId: string, displayName = 'Kid') {
  const [c] = await db.insert(children)
    .values({ displayName }).returning();
  await db.insert(childParentLinks)
    .values({ childId: c.id, parentId, displayName })
    .onConflictDoNothing();
  return c;
}
export async function seedDevice(childId: string, token = `tok-${Date.now()}`) {
  const [d] = await db.insert(devices)
    .values({ childId, deviceTokenHash: hashToken(token), platform: 'android' })
    .returning();
  return { device: d, token };
}
