ALTER TYPE "alert_type" ADD VALUE IF NOT EXISTS 'app_usage_limit_exceeded';

DO $$ BEGIN
  CREATE TYPE "app_usage_limit_type" AS ENUM ('total', 'app');
EXCEPTION
  WHEN duplicate_object THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS "app_usage_limits" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
  "parent_id" uuid NOT NULL REFERENCES "parents"("id") ON DELETE cascade,
  "child_id" uuid NOT NULL REFERENCES "children"("id") ON DELETE cascade,
  "type" "app_usage_limit_type" NOT NULL,
  "package_name" text,
  "app" text,
  "category" text,
  "limit_minutes" integer NOT NULL,
  "active" boolean DEFAULT true NOT NULL,
  "created_at" timestamp with time zone DEFAULT now() NOT NULL,
  "updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE INDEX IF NOT EXISTS "app_usage_limits_parent_child_idx" ON "app_usage_limits" ("parent_id", "child_id");
CREATE INDEX IF NOT EXISTS "app_usage_limits_child_active_idx" ON "app_usage_limits" ("child_id", "active");
CREATE UNIQUE INDEX IF NOT EXISTS "app_usage_limits_total_unique_idx"
  ON "app_usage_limits" ("parent_id", "child_id")
  WHERE "type" = 'total';
CREATE UNIQUE INDEX IF NOT EXISTS "app_usage_limits_app_package_unique_idx"
  ON "app_usage_limits" ("parent_id", "child_id", "package_name")
  WHERE "type" = 'app' AND "package_name" IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS "app_usage_limits_app_name_unique_idx"
  ON "app_usage_limits" ("parent_id", "child_id", "app")
  WHERE "type" = 'app' AND "package_name" IS NULL;

CREATE TABLE IF NOT EXISTS "app_usage_limit_events" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
  "limit_id" uuid NOT NULL REFERENCES "app_usage_limits"("id") ON DELETE cascade,
  "parent_id" uuid NOT NULL REFERENCES "parents"("id") ON DELETE cascade,
  "child_id" uuid NOT NULL REFERENCES "children"("id") ON DELETE cascade,
  "day" date NOT NULL,
  "usage_minutes" integer NOT NULL,
  "limit_minutes" integer NOT NULL,
  "alert_id" uuid REFERENCES "alerts"("id") ON DELETE set null,
  "created_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS "app_usage_limit_events_limit_day_idx"
  ON "app_usage_limit_events" ("limit_id", "day");
CREATE INDEX IF NOT EXISTS "app_usage_limit_events_parent_child_idx"
  ON "app_usage_limit_events" ("parent_id", "child_id", "day");
