ALTER TABLE "devices" ADD COLUMN IF NOT EXISTS "permission_status" jsonb;
ALTER TABLE "devices" ADD COLUMN IF NOT EXISTS "permission_status_checked_at" timestamp with time zone;
