CREATE TABLE IF NOT EXISTS "child_parent_links" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
  "child_id" uuid NOT NULL,
  "parent_id" uuid NOT NULL,
  "display_name" text NOT NULL,
  "role" text DEFAULT 'caregiver' NOT NULL,
  "created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
ALTER TABLE "pairing_codes" ADD COLUMN IF NOT EXISTS "created_by_parent_id" uuid;
--> statement-breakpoint
DO $$ BEGIN
 ALTER TABLE "child_parent_links" ADD CONSTRAINT "child_parent_links_child_id_children_id_fk"
 FOREIGN KEY ("child_id") REFERENCES "public"."children"("id") ON DELETE cascade ON UPDATE no action;
EXCEPTION
 WHEN duplicate_object THEN null;
END $$;
--> statement-breakpoint
DO $$ BEGIN
 ALTER TABLE "child_parent_links" ADD CONSTRAINT "child_parent_links_parent_id_parents_id_fk"
 FOREIGN KEY ("parent_id") REFERENCES "public"."parents"("id") ON DELETE cascade ON UPDATE no action;
EXCEPTION
 WHEN duplicate_object THEN null;
END $$;
--> statement-breakpoint
DO $$ BEGIN
 ALTER TABLE "pairing_codes" ADD CONSTRAINT "pairing_codes_created_by_parent_id_parents_id_fk"
 FOREIGN KEY ("created_by_parent_id") REFERENCES "public"."parents"("id") ON DELETE cascade ON UPDATE no action;
EXCEPTION
 WHEN duplicate_object THEN null;
END $$;
--> statement-breakpoint
CREATE INDEX IF NOT EXISTS "child_parent_links_parent_idx" ON "child_parent_links" USING btree ("parent_id");
--> statement-breakpoint
CREATE INDEX IF NOT EXISTS "child_parent_links_child_idx" ON "child_parent_links" USING btree ("child_id");
--> statement-breakpoint
CREATE UNIQUE INDEX IF NOT EXISTS "child_parent_links_unique_idx" ON "child_parent_links" USING btree ("child_id","parent_id");
--> statement-breakpoint
INSERT INTO "child_parent_links" ("child_id", "parent_id", "display_name", "role", "created_at")
SELECT "id", "parent_id", "display_name", 'owner', "created_at"
FROM "children"
ON CONFLICT ("child_id", "parent_id") DO NOTHING;
