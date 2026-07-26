ALTER TYPE "alert_type" ADD VALUE IF NOT EXISTS 'safe_zone_enter';
ALTER TYPE "alert_type" ADD VALUE IF NOT EXISTS 'safe_zone_exit';

ALTER TABLE "safe_zones" ADD COLUMN IF NOT EXISTS "parent_id" uuid;
ALTER TABLE "safe_zones" ADD COLUMN IF NOT EXISTS "source_child_id" uuid;
ALTER TABLE "safe_zones" ADD COLUMN IF NOT EXISTS "active" boolean DEFAULT true NOT NULL;

UPDATE "safe_zones" sz
SET
  "parent_id" = COALESCE(sz."parent_id", c."parent_id"),
  "source_child_id" = COALESCE(sz."source_child_id", sz."child_id")
FROM "children" c
WHERE sz."child_id" = c."id";

ALTER TABLE "safe_zones" ALTER COLUMN "parent_id" SET NOT NULL;
ALTER TABLE "safe_zones" ALTER COLUMN "child_id" DROP NOT NULL;
ALTER TABLE "safe_zones" DROP CONSTRAINT IF EXISTS "safe_zones_child_id_children_id_fk";
UPDATE "safe_zones" SET "child_id" = NULL;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'safe_zones_parent_id_parents_id_fk'
  ) THEN
    ALTER TABLE "safe_zones"
      ADD CONSTRAINT "safe_zones_parent_id_parents_id_fk"
      FOREIGN KEY ("parent_id") REFERENCES "parents"("id") ON DELETE cascade;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'safe_zones_source_child_id_children_id_fk'
  ) THEN
    ALTER TABLE "safe_zones"
      ADD CONSTRAINT "safe_zones_source_child_id_children_id_fk"
      FOREIGN KEY ("source_child_id") REFERENCES "children"("id") ON DELETE set null;
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS "safe_zones_parent_time_idx"
  ON "safe_zones" ("parent_id", "created_at");

ALTER TABLE "alerts" ADD COLUMN IF NOT EXISTS "parent_id" uuid;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'alerts_parent_id_parents_id_fk'
  ) THEN
    ALTER TABLE "alerts"
      ADD CONSTRAINT "alerts_parent_id_parents_id_fk"
      FOREIGN KEY ("parent_id") REFERENCES "parents"("id") ON DELETE cascade;
  END IF;
END $$;

CREATE TABLE IF NOT EXISTS "safe_zone_states" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
  "parent_id" uuid NOT NULL REFERENCES "parents"("id") ON DELETE cascade,
  "child_id" uuid NOT NULL REFERENCES "children"("id") ON DELETE cascade,
  "zone_id" uuid NOT NULL REFERENCES "safe_zones"("id") ON DELETE cascade,
  "is_inside" boolean NOT NULL,
  "last_transition_at" timestamp with time zone,
  "updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS "safe_zone_states_child_zone_idx"
  ON "safe_zone_states" ("parent_id", "child_id", "zone_id");
