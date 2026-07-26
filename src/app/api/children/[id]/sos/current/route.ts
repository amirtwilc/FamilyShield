import { requireParent } from '@/lib/auth/parent';
import { ok, err } from '@/lib/http';
import { assertChildOwned } from '@/lib/ownership';
import { sosStateForChild } from '@/lib/sos';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ id: string }> };

export async function GET(_req: Request, { params }: Ctx) {
  const a = await requireParent(_req); if ('response' in a) return a.response;
  const { id } = await params;
  if (!(await assertChildOwned(a.parentId, id))) return err('not_found', 'Child not found', 404);
  return ok(await sosStateForChild(id));
}
