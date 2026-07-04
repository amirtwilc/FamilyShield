import { z } from 'zod';
export const statusSchema = z.object({
  battery_level: z.number().int().min(0).max(100).optional(),
  is_charging: z.boolean().optional(),
  fcm_token: z.string().min(1).optional(),
});
