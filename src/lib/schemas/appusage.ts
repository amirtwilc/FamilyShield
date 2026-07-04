import { z } from 'zod';

export const reportUsageSchema = z.object({
  items: z.array(z.object({
    app: z.string().trim().min(1).max(64),
    category: z.string().trim().min(1).max(32),
    minutes: z.number().int().min(0).max(1440),
    day: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional(),
  })).min(1).max(100),
});
