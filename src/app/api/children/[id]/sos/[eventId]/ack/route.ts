import { requireParent } from '@/lib/auth/parent';
import { ok, err } from '@/lib/http';
import { assertChildOwned } from '@/lib/ownership';
import { acknowledgeSos } from '@/lib/sos';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string; eventId: string }> };

export async function POST(req: Request, { params }: Ctx) {
  const a = await requireParent(req); if ('response' in a) return a.response;
  const { id, eventId } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  const result = await acknowledgeSos(id, a.parentId, eventId);
  if (!result) return err('not_found', 'Active SOS not found', 404);
  return ok(result);
}
