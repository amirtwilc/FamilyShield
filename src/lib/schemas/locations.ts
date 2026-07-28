import { z } from 'zod';

function positiveEnvNumber(name: string, fallback: number) {
  const value = Number(process.env[name] ?? fallback);
  return Number.isFinite(value) && value > 0 ? value : fallback;
}

export const LOCATION_MAX_FUTURE_MIN = positiveEnvNumber('LOCATION_MAX_FUTURE_MIN', 10);
export const LOCATION_MAX_BACKFILL_DAYS = positiveEnvNumber('LOCATION_MAX_BACKFILL_DAYS', 90);

export function isAcceptableLocationTimestamp(value: string, nowMs = Date.now()): boolean {
  const timestamp = Date.parse(value);
  if (!Number.isFinite(timestamp)) return false;
  return timestamp <= nowMs + LOCATION_MAX_FUTURE_MIN * 60_000
    && timestamp >= nowMs - LOCATION_MAX_BACKFILL_DAYS * 24 * 60 * 60_000;
}

export const locationPoint = z.object({
  lat: z.number().min(-90).max(90),
  lng: z.number().min(-180).max(180),
  recorded_at: z.string().datetime(),
  speed: z.number().nonnegative().optional(),
  accuracy: z.number().nonnegative().optional(),
  battery_level: z.number().int().min(0).max(100).optional(),
}).refine((point) => isAcceptableLocationTimestamp(point.recorded_at), {
  message: `Location timestamp must be within the last ${LOCATION_MAX_BACKFILL_DAYS} days and no more than ${LOCATION_MAX_FUTURE_MIN} minutes ahead`,
  path: ['recorded_at'],
});
export const locationBatch = z.object({
  points: z.array(locationPoint).min(1).max(Number(process.env.MAX_LOCATION_BATCH ?? 200)),
});
