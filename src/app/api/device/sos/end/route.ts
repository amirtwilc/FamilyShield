import { requireDevice } from '@/lib/auth/device';
import { ok } from '@/lib/http';
import { endSos } from '@/lib/sos';
import { sosEndSchema } from '@/lib/schemas/sos';
import { parseBody } from '@/lib/validate';

export const runtime = 'nodejs';

export async function POST(req: Request) {
  const a = await requireDevice(req); if ('response' in a) return a.response;
  const p = await parseBody(req, sosEndSchema); if ('response' in p) return p.response;
  return ok(await endSos(a.device, p.data.reason ?? 'child_ended'));
}
