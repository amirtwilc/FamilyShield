import { fireOfflineSweep } from '@/lib/alerts/engine';
import { ok } from '@/lib/http';
import { requireCron } from '@/lib/cron';

export const runtime = 'nodejs';

export async function GET(req: Request) {
  const denied = requireCron(req); if (denied) return denied;
  return ok(await fireOfflineSweep());
}
