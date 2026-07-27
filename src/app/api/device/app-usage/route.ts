import { requireDevice } from '@/lib/auth/device';
import { parseBody } from '@/lib/validate';
import { ok } from '@/lib/http';
import { reportUsageSchema } from '@/lib/schemas/appusage';
import { upsertAppUsageReport } from '@/lib/app-usage-ingest';

export const runtime = 'nodejs';

/** Kid device reports per-app screen time (from UsageStatsManager). Upserts one
 *  row per (child, app, day) so re-reporting a day overwrites rather than dupes. */
export async function POST(req: Request) {
  const a = await requireDevice(req); if ('response' in a) return a.response;
  const p = await parseBody(req, reportUsageSchema); if ('response' in p) return p.response;

  const result = await upsertAppUsageReport(a.device, p.data.items, { touchLastSeen: true });
  return ok(result);
}
