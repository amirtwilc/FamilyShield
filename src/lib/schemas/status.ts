import { z } from 'zod';
export const permissionStatusSchema = z.object({
  g: z.enum(['g', 'x']),
  r: z.number().int().min(0).max(63),
  m: z.number().int().min(0).max(63),
});
export const statusSchema = z.object({
  battery_level: z.number().int().min(0).max(100).optional(),
  is_charging: z.boolean().optional(),
  fcm_token: z.string().min(1).optional(),
  p: permissionStatusSchema.optional(),
});
