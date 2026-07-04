import { z } from 'zod';

export const createZoneSchema = z.object({
  name: z.string().min(1).max(80),
  lat: z.number().min(-90).max(90),
  lng: z.number().min(-180).max(180),
  radiusM: z.number().int().min(10).max(50000),
  notifyOnEnter: z.boolean().optional(),
  notifyOnExit: z.boolean().optional(),
  dwellMinutes: z.number().int().min(1).max(1440).optional(),
});
