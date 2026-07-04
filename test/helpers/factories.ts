import { db } from '@/db/client';
import { childParentLinks, children, devices, parents } from '@/db/schema';
import { hashToken } from '@/lib/auth/device';

export async function seedParent(email = `p${Date.now()}@t.io`) {
  const [p] = await db.insert(parents)
    .values({ email, passwordHash: 'x', fcmToken: `fcm-${Date.now()}` }).returning();
  return p;
}
export async function seedChild(parentId: string, displayName = 'Kid') {
  const [c] = await db.insert(children)
    .values({ parentId, displayName }).returning();
  await db.insert(childParentLinks)
    .values({ childId: c.id, parentId, displayName, role: 'owner' })
    .onConflictDoNothing();
  return c;
}
export async function seedDevice(childId: string, token = `tok-${Date.now()}`) {
  const [d] = await db.insert(devices)
    .values({ childId, deviceTokenHash: hashToken(token), platform: 'android' })
    .returning();
  return { device: d, token };
}
