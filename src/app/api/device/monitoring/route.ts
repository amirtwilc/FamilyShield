import { requireDevice } from '@/lib/auth/device';
import { ok } from '@/lib/http';
import { monitoringInfo } from '@/lib/monitoring';

export const runtime = 'nodejs';

export async function GET(req: Request) {
  const a = await requireDevice(req); if ('response' in a) return a.response;
  return ok(await monitoringInfo(a.device.childId));
}
