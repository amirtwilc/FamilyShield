import { z } from 'zod';

export const appUsageReportItemSchema = z.object({
  app: z.string().trim().min(1).max(64),
  package_name: z.string().trim().min(1).max(128).optional(),
  category: z.string().trim().min(1).max(32),
  minutes: z.number().int().min(0).max(1440),
  day: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).optional(),
  is_relevant: z.boolean().optional(),
  hidden_reason: z.string().trim().min(1).max(64).optional(),
});

export const reportUsageSchema = z.object({
  items: z.array(appUsageReportItemSchema).min(1).max(100),
});

export const appUsageLimitSchema = z.object({
  type: z.enum(['total', 'app']),
  packageName: z.string().trim().min(1).max(128).optional(),
  app: z.string().trim().min(1).max(64).optional(),
  category: z.string().trim().min(1).max(32).optional(),
  limitMinutes: z.number().int().min(1).max(1440),
  active: z.boolean().optional(),
}).superRefine((value, ctx) => {
  if (value.type === 'app' && !value.packageName && !value.app) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      message: 'App limits require packageName or app',
      path: ['app'],
    });
  }
});

export const updateAppUsageLimitSchema = z.object({
  limitMinutes: z.number().int().min(1).max(1440).optional(),
  active: z.boolean().optional(),
}).refine((value) => value.limitMinutes !== undefined || value.active !== undefined, {
  message: 'Provide limitMinutes or active',
});
