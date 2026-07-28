import { eq } from 'drizzle-orm';
import { db } from '../../db/client';
import { parents } from '../../db/schema';
import { verifyAccess } from './jwt';
import { err } from '../http';

function bearer(req: Request): string | null {
  const h = req.headers.get('authorization') ?? '';
  return h.startsWith('Bearer ') ? h.slice(7) : null;
}
export async function requireParent(req: Request): Promise<{ parentId: string } | { response: Response }> {
  const t = bearer(req);
  if (!t) return { response: err('unauthorized', 'Missing bearer token', 401) };
  try {
    const parentId = await verifyAccess(t);
    const [parent] = await db.select({ id: parents.id }).from(parents).where(eq(parents.id, parentId));
    if (!parent) return { response: err('unauthorized', 'Account no longer exists', 401) };
    return { parentId };
  }
  catch { return { response: err('unauthorized', 'Invalid token', 401) }; }
}
