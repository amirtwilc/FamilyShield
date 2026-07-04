import { verifyAccess } from './jwt';
import { err } from '../http';

function bearer(req: Request): string | null {
  const h = req.headers.get('authorization') ?? '';
  return h.startsWith('Bearer ') ? h.slice(7) : null;
}
export async function requireParent(req: Request): Promise<{ parentId: string } | { response: Response }> {
  const t = bearer(req);
  if (!t) return { response: err('unauthorized', 'Missing bearer token', 401) };
  try { return { parentId: await verifyAccess(t) }; }
  catch { return { response: err('unauthorized', 'Invalid token', 401) }; }
}
