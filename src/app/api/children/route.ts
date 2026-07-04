import { eq, inArray } from 'drizzle-orm';
import { db } from '@/db/client';
import { childParentLinks, children, devices } from '@/db/schema';
import { requireParent } from '@/lib/auth/parent';
import { parseBody } from '@/lib/validate';
import { ok, err } from '@/lib/http';
import { createChildSchema } from '@/lib/schemas/children';
import { enforceCanAddChild } from '@/lib/retention';
import { nextAvailableAvatar } from '@/lib/avatars';

export const runtime = 'nodejs';

export async function POST(req: Request) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const p = await parseBody(req, createChildSchema); if ('response' in p) return p.response;
  const allowed = await enforceCanAddChild(a.parentId);
  if (!allowed.ok) {
    return err('tier_limit_exceeded', `Your ${allowed.tierCode} tier allows up to ${allowed.maxChildren} monitored children`, 403);
  }
  const existing = await db.select({ avatar: children.avatar }).from(childParentLinks)
    .innerJoin(children, eq(childParentLinks.childId, children.id))
    .where(eq(childParentLinks.parentId, a.parentId));
  const avatar = p.data.avatar ?? nextAvailableAvatar(existing.map((c) => c.avatar), p.data.displayName);
  const [row] = await db.insert(children)
    .values({ parentId: a.parentId, displayName: p.data.displayName, avatar }).returning();
  await db.insert(childParentLinks).values({
    childId: row.id, parentId: a.parentId, displayName: p.data.displayName, role: 'owner',
  });
  return ok({ ...row, displayName: p.data.displayName }, 201);
}

export async function GET(req: Request) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const kids = await db.select({
    id: children.id,
    parentId: children.parentId,
    displayName: childParentLinks.displayName,
    avatar: children.avatar,
    createdAt: children.createdAt,
  }).from(childParentLinks)
    .innerJoin(children, eq(childParentLinks.childId, children.id))
    .where(eq(childParentLinks.parentId, a.parentId));
  if (kids.length === 0) return ok({ children: [] });

  // One query for all devices (no N+1), and only the columns the client needs —
  // never the device_token_hash / fcm_token.
  const ids = kids.map((k) => k.id);
  const devs = await db.select({
    id: devices.id, childId: devices.childId, platform: devices.platform, model: devices.model,
    batteryLevel: devices.batteryLevel, isCharging: devices.isCharging, lastSeenAt: devices.lastSeenAt,
    revokedAt: devices.revokedAt,
  }).from(devices).where(inArray(devices.childId, ids));

  const byChild = new Map<string, typeof devs>();
  for (const d of devs) (byChild.get(d.childId) ?? byChild.set(d.childId, []).get(d.childId)!).push(d);
  const result = kids.map((k) => ({ ...k, devices: byChild.get(k.id) ?? [] }));
  return ok({ children: result });
}
