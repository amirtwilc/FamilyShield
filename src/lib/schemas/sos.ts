import { z } from 'zod';
import { locationPoint } from './locations';

const localDay = z.string().regex(/^\d{4}-\d{2}-\d{2}$/);

export const sosStartSchema = z.object({
  timezone: z.string().trim().min(1).max(80).default('UTC'),
  local_day: localDay,
  location: locationPoint.optional(),
});

export const sosLocationSchema = z.object({
  timezone: z.string().trim().min(1).max(80).default('UTC'),
  local_day: localDay,
  location: locationPoint,
});

export const sosEndSchema = z.object({
  reason: z.string().trim().min(1).max(80).default('child_ended'),
});

export const urgentAlertSchema = z.object({
  body: z.string().trim().min(1).max(500),
});
