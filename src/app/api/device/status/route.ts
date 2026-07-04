import { eq } from 'drizzle-orm';
import { db } from '@/db/client';
import { devices } from '@/db/schema';
import { requireDevice } from '@/lib/auth/device';
import { parseBody } from '@/lib/validate';
import { ok } from '@/lib/http';
import { fireLowBatteryIfNeeded } from '@/lib/alerts/engine';
import { statusSchema } from '@/lib/schemas/status';

export const runtime = 'nodejs';

export async function POST(req: Request) {
  const a = await requireDevice(req); if ('response' in a) return a.response;
  const p = await parseBody(req, statusSchema); if ('response' in p) return p.response;
  await db.update(devices).set({
    batteryLevel: p.data.battery_level ?? a.device.batteryLevel,
    isCharging: p.data.is_charging ?? a.device.isCharging,
    fcmToken: p.data.fcm_token ?? a.device.fcmToken,
    lastSeenAt: new Date(),
  }).where(eq(devices.id, a.device.id));
  const [fresh] = await db.select().from(devices).where(eq(devices.id, a.device.id));
  await fireLowBatteryIfNeeded(fresh);
  return ok({ ok: true });
}
