ALTER TYPE "alert_type" ADD VALUE IF NOT EXISTS 'kid_sos_started';
ALTER TYPE "alert_type" ADD VALUE IF NOT EXISTS 'kid_sos_ended';
ALTER TYPE "alert_type" ADD VALUE IF NOT EXISTS 'urgent_alert';

ALTER TABLE "messages" ADD COLUMN IF NOT EXISTS "priority" text NOT NULL DEFAULT 'normal';

CREATE TABLE IF NOT EXISTS "sos_events" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
  "child_id" uuid NOT NULL REFERENCES "children"("id") ON DELETE cascade,
  "device_id" uuid NOT NULL REFERENCES "devices"("id") ON DELETE cascade,
  "status" text DEFAULT 'active' NOT NULL,
  "started_at" timestamp with time zone DEFAULT now() NOT NULL,
  "ended_at" timestamp with time zone,
  "ended_reason" text,
  "timezone" text DEFAULT 'UTC' NOT NULL,
  "local_day" date NOT NULL,
  "last_location" geometry(Point,4326),
  "last_location_at" timestamp with time zone,
  "last_battery_level" integer,
  "high_rate_limit_seconds" integer NOT NULL,
  "high_rate_interval_seconds" integer NOT NULL
);

CREATE INDEX IF NOT EXISTS "sos_events_child_status_idx" ON "sos_events" ("child_id", "status");
CREATE INDEX IF NOT EXISTS "sos_events_child_started_idx" ON "sos_events" ("child_id", "started_at");

CREATE TABLE IF NOT EXISTS "sos_daily_usage" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
  "child_id" uuid NOT NULL REFERENCES "children"("id") ON DELETE cascade,
  "day" date NOT NULL,
  "timezone" text NOT NULL,
  "used_seconds" integer DEFAULT 0 NOT NULL,
  "updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS "sos_daily_usage_child_day_idx" ON "sos_daily_usage" ("child_id", "day");

CREATE TABLE IF NOT EXISTS "sos_event_receipts" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
  "sos_event_id" uuid NOT NULL REFERENCES "sos_events"("id") ON DELETE cascade,
  "parent_id" uuid NOT NULL REFERENCES "parents"("id") ON DELETE cascade,
  "acknowledged_at" timestamp with time zone,
  "created_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS "sos_event_receipts_event_parent_idx" ON "sos_event_receipts" ("sos_event_id", "parent_id");
