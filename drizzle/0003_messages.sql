CREATE TABLE IF NOT EXISTS "messages" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"child_id" uuid NOT NULL,
	"sender" text NOT NULL,
	"body" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"read_at" timestamp with time zone,
	CONSTRAINT "messages_sender_check" CHECK ("sender" IN ('parent', 'child'))
);
--> statement-breakpoint
DO $$ BEGIN
	ALTER TABLE "messages" ADD CONSTRAINT "messages_child_id_children_id_fk"
		FOREIGN KEY ("child_id") REFERENCES "children"("id") ON DELETE cascade ON UPDATE no action;
EXCEPTION WHEN duplicate_object THEN null; END $$;
--> statement-breakpoint
CREATE INDEX IF NOT EXISTS "messages_child_time_idx" ON "messages" ("child_id","created_at");
