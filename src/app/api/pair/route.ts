import { and, eq, sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { childParentLinks, children, devices, pairingCodes } from '@/db/schema';
import { parseBody } from '@/lib/validate';
import { ok, err } from '@/lib/http';
import { bearer, createDeviceToken, deviceFromToken } from '@/lib/auth/device';
import { pairSchema } from '@/lib/schemas/pair';
import { memoryLimiter, clientKey, tooMany } from '@/lib/ratelimit';
import { monitoringInfo } from '@/lib/monitoring';

export const runtime = 'nodejs';

const pairLimiter = memoryLimiter(10, 60_000);

export async function POST(req: Request) {
  if (!pairLimiter.check(clientKey(req, 'pair')).allowed) return tooMany();
  const p = await parseBody(req, pairSchema); if ('response' in p) return p.response;

  const existingToken = bearer(req);
  const existingDevice = existingToken ? await deviceFromToken(existingToken) : null;
  if (existingToken && !existingDevice) return err('unauthorized', 'Unknown or revoked device', 401);

  const paired = await db.transaction(async (tx) => {
    const locked = await tx.execute(sql`
      SELECT id, child_id, created_by_parent_id
      FROM pairing_codes
      WHERE code = ${p.data.code}
        AND consumed_at IS NULL
        AND expires_at > now()
      FOR UPDATE`);
    const claimed = locked.rows[0] as { id: string; child_id: string; created_by_parent_id: string | null } | undefined;
    if (!claimed) return null;

    if (!existingDevice) {
      await tx.update(pairingCodes).set({ consumedAt: new Date() }).where(eq(pairingCodes.id, claimed.id));
      const { token, hash } = createDeviceToken();
      await tx.insert(devices).values({
        childId: claimed.child_id, deviceTokenHash: hash,
        platform: p.data.platform, model: p.data.model ?? null, lastSeenAt: new Date(),
      });
      return { type: 'initial' as const, token, childId: claimed.child_id };
    }

    const [sourceLink] = await tx.select().from(childParentLinks)
      .where(and(
        eq(childParentLinks.childId, claimed.child_id),
        claimed.created_by_parent_id
          ? eq(childParentLinks.parentId, claimed.created_by_parent_id)
          : undefined,
      )).limit(1);
    if (!sourceLink) return { type: 'error' as const, code: 'invalid_code', message: 'Code is invalid, expired, or already used', status: 400 };

    const [alreadyLinked] = await tx.select({ id: childParentLinks.id }).from(childParentLinks)
      .where(and(eq(childParentLinks.childId, existingDevice.childId), eq(childParentLinks.parentId, sourceLink.parentId)));
    if (alreadyLinked) return { type: 'error' as const, code: 'already_linked', message: 'This parent already monitors this child', status: 400 };

    const [sourceDevice] = await tx.select({ id: devices.id }).from(devices)
      .where(eq(devices.childId, claimed.child_id)).limit(1);
    if (sourceDevice) return { type: 'error' as const, code: 'child_already_paired', message: 'This code belongs to a child that already has a paired device', status: 400 };

    await tx.update(pairingCodes).set({ consumedAt: new Date() }).where(eq(pairingCodes.id, claimed.id));
    await tx.insert(childParentLinks).values({
      childId: existingDevice.childId,
      parentId: sourceLink.parentId,
      displayName: sourceLink.displayName,
      role: 'caregiver',
    });
    if (claimed.child_id !== existingDevice.childId) {
      await tx.delete(children).where(eq(children.id, claimed.child_id));
    }
    return { type: 'additional_parent' as const, childId: existingDevice.childId };
  });

  if (!paired) return err('invalid_code', 'Code is invalid, expired, or already used', 400);
  if (paired.type === 'error') return err(paired.code, paired.message, paired.status);
  if (paired.type === 'initial') return ok({ deviceToken: paired.token, childId: paired.childId }, 201);
  return ok(await monitoringInfo(paired.childId), 200);
}
