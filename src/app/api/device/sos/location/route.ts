import { requireDevice } from '@/lib/auth/device';
import { ok } from '@/lib/http';
import { recordSosLocation } from '@/lib/sos';
import { sosLocationSchema } from '@/lib/schemas/sos';
import { parseBody } from '@/lib/validate';

export const runtime = 'nodejs';

export async function POST(req: Request) {
  const a = await requireDevice(req); if ('response' in a) return a.response;
  const p = await parseBody(req, sosLocationSchema); if ('response' in p) return p.response;
  return ok(await recordSosLocation(a.device, p.data));
}
