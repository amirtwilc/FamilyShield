import { sql } from 'drizzle-orm';
import { db } from '@/db/client';
import { requireDevice } from '@/lib/auth/device';
import { parseBody } from '@/lib/validate';
import { ok, err } from '@/lib/http';
import { sendMessageSchema } from '@/lib/schemas/messages';
import { pageMessages } from '@/lib/messages';
import { monitorLink } from '@/lib/monitoring';

export const runtime = 'nodejs';
type Ctx = { params: Promise<{ parentId: string }> };

export async function GET(req: Request, { params }: Ctx) {
  const a = await requireDevice(req); if ('response' in a) return a.response;
  const { parentId } = await params;
  if (!(await monitorLink(a.device.childId, parentId))) return err('not_found', 'Monitor not found', 404);
  await db.execute(sql`
    UPDATE messages SET read_at = now()
    WHERE child_id = ${a.device.childId} AND parent_id = ${parentId}
      AND sender = 'parent' AND read_at IS NULL`);
  return ok(await pageMessages(a.device.childId, new URL(req.url), parentId));
}

export async function POST(req: Request, { params }: Ctx) {
  const a = await requireDevice(req); if ('response' in a) return a.response;
  const { parentId } = await params;
  if (!(await monitorLink(a.device.childId, parentId))) return err('not_found', 'Monitor not found', 404);
  const p = await parseBody(req, sendMessageSchema); if ('response' in p) return p.response;
  const r = await db.execute(sql`
    INSERT INTO messages (child_id, parent_id, sender, body)
    VALUES (${a.device.childId}, ${parentId}, 'child', ${p.data.body})
    RETURNING id, sender, body, created_at, read_at`);
  await db.execute(sql`UPDATE devices SET last_seen_at = now() WHERE id = ${a.device.id}`);
  return ok(r.rows[0], 201);
}
