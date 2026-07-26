import { requireDevice } from '@/lib/auth/device';
import { ok } from '@/lib/http';
import { sosStateForChild } from '@/lib/sos';

export const runtime = 'nodejs';

export async function GET(req: Request) {
  const a = await requireDevice(req); if ('response' in a) return a.response;
  const localDay = new URL(req.url).searchParams.get('local_day');
  return ok(await sosStateForChild(a.device.childId, localDay));
}
