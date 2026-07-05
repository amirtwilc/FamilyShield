ALTER TABLE "devices" ADD COLUMN IF NOT EXISTS "app_usage_access_granted" boolean;
ALTER TABLE "devices" ADD COLUMN IF NOT EXISTS "app_usage_access_checked_at" timestamp with time zone;
