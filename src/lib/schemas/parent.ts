import { z } from 'zod';
export const pushTokenSchema = z.object({ fcm_token: z.string().trim().min(1).max(4096) });
export const alertQuery = z.object({
  limit: z.coerce.number().int().min(1).max(200).default(50),
  cursor: z.string().max(2048).optional(),
});
export const historyQuery = z.object({
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  cursor: z.string().optional(),
  limit: z.coerce.number().int().min(1).max(500).default(200),
});
export const appUsageQuery = z.object({
  date: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional(),
});
