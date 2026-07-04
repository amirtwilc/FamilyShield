import { z } from 'zod';
export const pairSchema = z.object({
  code: z.string().regex(/^\d{6}$/),
  platform: z.string().min(1).max(40),
  model: z.string().max(120).optional(),
});
