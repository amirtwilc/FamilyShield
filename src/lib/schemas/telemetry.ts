import { z } from 'zod';
import { appUsageReportItemSchema } from './appusage';
import { locationPoint } from './locations';
import { statusSchema } from './status';

export const deviceTelemetrySchema = z.object({
  status: statusSchema.optional(),
  location: locationPoint.optional(),
  locations: z.array(locationPoint).max(Number(process.env.MAX_LOCATION_BATCH ?? 200)).optional(),
  app_usage: z.object({
    access_granted: z.boolean(),
    items: z.array(appUsageReportItemSchema).max(100).default([]),
  }).optional(),
});
