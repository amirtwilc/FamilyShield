import { requireDevice } from '@/lib/auth/device';
import { ok } from '@/lib/http';
import { startSos } from '@/lib/sos';
import { sosStartSchema } from '@/lib/schemas/sos';
import { parseBody } from '@/lib/validate';

export const runtime = 'nodejs';

export async function POST(req: Request) {
  const a = await requireDevice(req); if ('response' in a) return a.response;
  const p = await parseBody(req, sosStartSchema); if ('response' in p) return p.response;
  return ok(await startSos(a.device, p.data), 201);
}
