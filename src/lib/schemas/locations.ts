import { z } from 'zod';
export const locationPoint = z.object({
  lat: z.number().min(-90).max(90),
  lng: z.number().min(-180).max(180),
  recorded_at: z.string().datetime(),
  speed: z.number().nonnegative().optional(),
  accuracy: z.number().nonnegative().optional(),
  battery_level: z.number().int().min(0).max(100).optional(),
});
export const locationBatch = z.object({
  points: z.array(locationPoint).min(1).max(Number(process.env.MAX_LOCATION_BATCH ?? 200)),
});
