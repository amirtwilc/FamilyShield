CREATE TABLE IF NOT EXISTS "subscription_tiers" (
  "code" text PRIMARY KEY NOT NULL,
  "name" text NOT NULL,
  "location_retention_days" integer NOT NULL,
  "max_children" integer NOT NULL,
  "is_active" boolean DEFAULT true NOT NULL,
  "created_at" timestamp with time zone DEFAULT now() NOT NULL,
  "updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
INSERT INTO "subscription_tiers" ("code", "name", "location_retention_days", "max_children", "is_active")
VALUES ('free', 'Free', 2, 5, true)
ON CONFLICT ("code") DO UPDATE SET
  "name" = EXCLUDED."name",
  "location_retention_days" = EXCLUDED."location_retention_days",
  "max_children" = EXCLUDED."max_children",
  "is_active" = EXCLUDED."is_active",
  "updated_at" = now();
--> statement-breakpoint
ALTER TABLE "parents" ADD COLUMN IF NOT EXISTS "tier_code" text DEFAULT 'free' NOT NULL;
--> statement-breakpoint
DO $$ BEGIN
 ALTER TABLE "parents" ADD CONSTRAINT "parents_tier_code_subscription_tiers_code_fk"
 FOREIGN KEY ("tier_code") REFERENCES "public"."subscription_tiers"("code") ON DELETE restrict ON UPDATE no action;
EXCEPTION
 WHEN duplicate_object THEN null;
END $$;
--> statement-breakpoint
ALTER TABLE "messages" ADD COLUMN IF NOT EXISTS "parent_id" uuid;
--> statement-breakpoint
UPDATE "messages" m
SET "parent_id" = owner."parent_id"
FROM (
  SELECT DISTINCT ON ("child_id") "child_id", "parent_id"
  FROM "child_parent_links"
  ORDER BY "child_id", CASE WHEN "role" = 'owner' THEN 0 ELSE 1 END, "created_at"
) owner
WHERE m."child_id" = owner."child_id" AND m."parent_id" IS NULL;
--> statement-breakpoint
DO $$ BEGIN
 ALTER TABLE "messages" ADD CONSTRAINT "messages_parent_id_parents_id_fk"
 FOREIGN KEY ("parent_id") REFERENCES "public"."parents"("id") ON DELETE cascade ON UPDATE no action;
EXCEPTION
 WHEN duplicate_object THEN null;
END $$;
--> statement-breakpoint
CREATE INDEX IF NOT EXISTS "messages_parent_child_time_id_idx" ON "messages" ("parent_id","child_id","created_at","id");
