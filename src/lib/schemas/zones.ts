import { z } from 'zod';

export const createZoneSchema = z.object({
  name: z.string().min(1).max(80),
  lat: z.number().min(-90).max(90),
  lng: z.number().min(-180).max(180),
  radiusM: z.number().int().min(50).max(2000).refine((v) => v % 10 === 0, 'Radius must be in 10m increments'),
  active: z.boolean().optional(),
  notifyOnEnter: z.boolean().optional(),
  notifyOnExit: z.boolean().optional(),
  dwellMinutes: z.number().int().min(1).max(1440).optional(),
});

export const updateZoneSchema = z.object({
  name: z.string().min(1).max(80).optional(),
  radiusM: z.number().int().min(50).max(2000).refine((v) => v % 10 === 0, 'Radius must be in 10m increments').optional(),
  active: z.boolean().optional(),
}).refine((v) => v.name !== undefined || v.radiusM !== undefined || v.active !== undefined, {
  message: 'At least one field must be provided',
});
