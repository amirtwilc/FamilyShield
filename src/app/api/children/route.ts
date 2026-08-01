import { eq, inArray, sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { childParentLinks, children, devices } from '@/db/schema';
import { requireParent } from '@/lib/auth/parent';
import { parseBody } from '@/lib/validate';
import { ok, err } from '@/lib/http';
import { createChildSchema } from '@/lib/schemas/children';
import { nextAvailableAvatar } from '@/lib/avatars';
import { isDeviceOnline } from '@/lib/device-status';

export const runtime = 'nodejs';

export async function POST(req: Request) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const p = await parseBody(req, createChildSchema); if ('response' in p) return p.response;
  const result = await db.transaction(async (tx) => {
    // Serialize quota checks for this parent so concurrent requests cannot
    // both observe the same remaining slot.
    await tx.execute(sql`SELECT id FROM parents WHERE id = ${a.parentId} FOR UPDATE`);
    const quota = await tx.execute(sql`
      SELECT p.tier_code, st.max_children, count(cpl.id)::int AS child_count
      FROM parents p
      JOIN subscription_tiers st ON st.code = p.tier_code
      LEFT JOIN child_parent_links cpl ON cpl.parent_id = p.id
      WHERE p.id = ${a.parentId}
      GROUP BY p.tier_code, st.max_children`);
    const limit = quota.rows[0] as { tier_code: string; max_children: number; child_count: number };
    if (Number(limit.child_count) >= Number(limit.max_children)) {
      return { allowed: false as const, tierCode: limit.tier_code, maxChildren: Number(limit.max_children) };
    }
    const existing = await tx.select({ avatar: children.avatar }).from(childParentLinks)
      .innerJoin(children, eq(childParentLinks.childId, children.id))
      .where(eq(childParentLinks.parentId, a.parentId));
    const avatar = p.data.avatar ?? nextAvailableAvatar(existing.map((c) => c.avatar), p.data.displayName);
    const [created] = await tx.insert(children)
      .values({ displayName: p.data.displayName, avatar, phoneNumber: p.data.phoneNumber ?? null }).returning();
    await tx.insert(childParentLinks).values({
      childId: created.id, parentId: a.parentId, displayName: p.data.displayName,
    });
    return { allowed: true as const, row: created };
  });
  if (!result.allowed) {
    return err('tier_limit_exceeded', `Your ${result.tierCode} tier allows up to ${result.maxChildren} monitored children`, 403);
  }
  return ok({ ...result.row, displayName: p.data.displayName }, 201);
}

export async function GET(req: Request) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const kids = await db.select({
    id: children.id,
    displayName: childParentLinks.displayName,
    avatar: children.avatar,
    phoneNumber: children.phoneNumber,
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
    revokedAt: devices.revokedAt, permissionStatus: devices.permissionStatus,
    permissionStatusCheckedAt: devices.permissionStatusCheckedAt,
  }).from(devices).where(inArray(devices.childId, ids));
  const devicesWithStatus = devs.map((d) => ({
    ...d,
    isOnline: isDeviceOnline(d),
  }));

  const byChild = new Map<string, typeof devicesWithStatus>();
  for (const d of devicesWithStatus) (byChild.get(d.childId) ?? byChild.set(d.childId, []).get(d.childId)!).push(d);
  const result = kids.map((k) => ({ ...k, devices: byChild.get(k.id) ?? [] }));
  return ok({ children: result });
}
