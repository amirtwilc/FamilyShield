-- Firebase owns parent authentication. Neon keeps the stable application
-- identity so existing children, devices, messages, and history are preserved.
ALTER TABLE "parents"
  ADD COLUMN IF NOT EXISTS "firebase_uid" text,
  ADD COLUMN IF NOT EXISTS "auth_migrated_at" timestamp with time zone;

-- The preflight command must be run before this migration. This UPDATE and the
-- unique index intentionally fail instead of silently merging duplicate users.
UPDATE "parents"
SET "email" = lower(btrim("email"))
WHERE "email" <> lower(btrim("email"));

ALTER TABLE "parents" DROP CONSTRAINT IF EXISTS "parents_email_unique";
CREATE UNIQUE INDEX IF NOT EXISTS "parents_email_normalized_unique_idx"
  ON "parents" (lower(btrim("email")));
CREATE UNIQUE INDEX IF NOT EXISTS "parents_firebase_uid_unique_idx"
  ON "parents" ("firebase_uid")
  WHERE "firebase_uid" IS NOT NULL;
