-- Equal guardians: the relationship table is the sole source of guardianship.
-- Removing one parent now cascades only that parent's link, not the shared child.
DROP INDEX IF EXISTS "children_parent_idx";
ALTER TABLE "children" DROP CONSTRAINT IF EXISTS "children_parent_id_parents_id_fk";
ALTER TABLE "children" DROP COLUMN IF EXISTS "parent_id";
ALTER TABLE "child_parent_links" DROP COLUMN IF EXISTS "role";

-- Preserve event ordering for safe-zone state when devices replay buffered data.
ALTER TABLE "safe_zone_states"
  ADD COLUMN IF NOT EXISTS "last_observed_at" timestamp with time zone;
UPDATE "safe_zone_states"
SET "last_observed_at" = GREATEST(
  "last_transition_at",
  "updated_at",
  (
    SELECT MAX(d."last_location_at")
    FROM "devices" d
    WHERE d."child_id" = "safe_zone_states"."child_id"
  )
)
WHERE "last_observed_at" IS NULL;

-- Retire duplicate/expired active pairing codes before enforcing uniqueness.
UPDATE "pairing_codes"
SET "consumed_at" = now()
WHERE "consumed_at" IS NULL AND "expires_at" <= now();

WITH duplicates AS (
  SELECT "id",
    row_number() OVER (
      PARTITION BY "code"
      ORDER BY "expires_at" DESC, "created_at" DESC, "id" DESC
    ) AS position
  FROM "pairing_codes"
  WHERE "consumed_at" IS NULL
)
UPDATE "pairing_codes" p
SET "consumed_at" = now()
FROM duplicates d
WHERE p."id" = d."id" AND d.position > 1;

DROP INDEX IF EXISTS "pairing_codes_active_idx";
CREATE UNIQUE INDEX IF NOT EXISTS "pairing_codes_active_unique_idx"
  ON "pairing_codes" ("code")
  WHERE "consumed_at" IS NULL;
CREATE INDEX IF NOT EXISTS "pairing_codes_expiry_idx"
  ON "pairing_codes" ("expires_at")
  WHERE "consumed_at" IS NULL;

-- Resolve any legacy duplicate SOS rows, then make active SOS state singular.
WITH active_events AS (
  SELECT "id",
    row_number() OVER (
      PARTITION BY "child_id"
      ORDER BY "started_at" DESC, "id" DESC
    ) AS position
  FROM "sos_events"
  WHERE "status" = 'active'
)
UPDATE "sos_events" e
SET "status" = 'ended',
    "ended_at" = COALESCE("ended_at", now()),
    "ended_reason" = COALESCE("ended_reason", 'duplicate_repaired')
FROM active_events a
WHERE e."id" = a."id" AND a.position > 1;

CREATE UNIQUE INDEX IF NOT EXISTS "sos_events_one_active_child_idx"
  ON "sos_events" ("child_id")
  WHERE "status" = 'active';

-- Shared, deployment-wide rate limiting for serverless/multi-instance backends.
CREATE TABLE IF NOT EXISTS "rate_limit_buckets" (
  "key_hash" text PRIMARY KEY,
  "count" integer NOT NULL,
  "window_started_at" timestamp with time zone NOT NULL,
  "expires_at" timestamp with time zone NOT NULL
);
CREATE INDEX IF NOT EXISTS "rate_limit_buckets_expiry_idx"
  ON "rate_limit_buckets" ("expires_at");
