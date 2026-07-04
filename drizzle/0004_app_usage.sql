CREATE TABLE IF NOT EXISTS "app_usage" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"child_id" uuid NOT NULL,
	"app" text NOT NULL,
	"category" text NOT NULL,
	"minutes" integer NOT NULL,
	"day" date NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
DO $$ BEGIN
	ALTER TABLE "app_usage" ADD CONSTRAINT "app_usage_child_id_children_id_fk"
		FOREIGN KEY ("child_id") REFERENCES "children"("id") ON DELETE cascade ON UPDATE no action;
EXCEPTION WHEN duplicate_object THEN null; END $$;
--> statement-breakpoint
CREATE INDEX IF NOT EXISTS "app_usage_child_day_idx" ON "app_usage" ("child_id","day");
--> statement-breakpoint
CREATE UNIQUE INDEX IF NOT EXISTS "app_usage_unique_idx" ON "app_usage" ("child_id","app","day");
