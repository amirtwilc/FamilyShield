import { ok } from '@/lib/http';
import { requireCron } from '@/lib/cron';
import { sweepLocationRetention } from '@/lib/retention';

export const runtime = 'nodejs';

export async function GET(req: Request) {
  const denied = requireCron(req); if (denied) return denied;
  return ok(await sweepLocationRetention());
}
