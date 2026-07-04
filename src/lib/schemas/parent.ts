import { z } from 'zod';
export const pushTokenSchema = z.object({ fcm_token: z.string().min(1) });
export const historyQuery = z.object({
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  cursor: z.string().optional(),
  limit: z.coerce.number().int().min(1).max(500).default(200),
});
