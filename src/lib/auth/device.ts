import { createHash, randomBytes } from 'node:crypto';
import { eq, and, isNull } from 'drizzle-orm';
import { db } from '../../db/client';
import { devices } from '../../db/schema';
import { err } from '../http';

/** Hashes a device token for storage (sha256 hex). Never store raw tokens. */
export function hashToken(token: string): string {
  return createHash('sha256').update(token).digest('hex');
}

/** Generates a fresh device pairing token + its stored hash. */
export function createDeviceToken(): { token: string; hash: string } {
  const token = randomBytes(32).toString('base64url');
  return { token, hash: hashToken(token) };
}

export function bearer(req: Request): string | null {
  const h = req.headers.get('authorization') ?? '';
  return h.startsWith('Bearer ') ? h.slice(7) : null;
}

export async function deviceFromToken(token: string): Promise<typeof devices.$inferSelect | null> {
  const [d] = await db.select().from(devices)
    .where(and(eq(devices.deviceTokenHash, hashToken(token)), isNull(devices.revokedAt)));
  return d ?? null;
}

export async function requireDevice(req: Request): Promise<{ device: typeof devices.$inferSelect } | { response: Response }> {
  const t = bearer(req);
  if (!t) return { response: err('unauthorized', 'Missing device token', 401) };
  const d = await deviceFromToken(t);
  if (!d) return { response: err('unauthorized', 'Unknown or revoked device', 401) };
  return { device: d };
}
