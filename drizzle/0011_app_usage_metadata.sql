ALTER TABLE "app_usage" ADD COLUMN IF NOT EXISTS "package_name" text;
ALTER TABLE "app_usage" ADD COLUMN IF NOT EXISTS "is_relevant" boolean DEFAULT true NOT NULL;
ALTER TABLE "app_usage" ADD COLUMN IF NOT EXISTS "hidden_reason" text;
ALTER TABLE "app_usage" ADD COLUMN IF NOT EXISTS "last_reported_at" timestamp with time zone DEFAULT now() NOT NULL;
--> statement-breakpoint
UPDATE "app_usage" SET "package_name" = "app" WHERE "package_name" IS NULL;
--> statement-breakpoint
ALTER TABLE "app_usage" ALTER COLUMN "package_name" SET NOT NULL;
--> statement-breakpoint
DROP INDEX IF EXISTS "app_usage_unique_idx";
--> statement-breakpoint
CREATE UNIQUE INDEX IF NOT EXISTS "app_usage_unique_idx" ON "app_usage" ("child_id","package_name","day");
--> statement-breakpoint
CREATE INDEX IF NOT EXISTS "app_usage_relevant_idx" ON "app_usage" ("child_id","day","is_relevant");
