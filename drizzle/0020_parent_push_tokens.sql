CREATE TABLE IF NOT EXISTS "parent_push_tokens" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
  "parent_id" uuid NOT NULL REFERENCES "parents"("id") ON DELETE cascade,
  "token" text NOT NULL,
  "created_at" timestamp with time zone DEFAULT now() NOT NULL,
  "updated_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS "parent_push_tokens_token_unique_idx"
  ON "parent_push_tokens" ("token");
CREATE INDEX IF NOT EXISTS "parent_push_tokens_parent_idx"
  ON "parent_push_tokens" ("parent_id");

-- Preserve the one legacy token each parent could previously store.
INSERT INTO "parent_push_tokens" ("parent_id", "token")
SELECT legacy."id", legacy."fcm_token"
FROM (
  SELECT DISTINCT ON ("fcm_token") "id", "fcm_token"
  FROM "parents"
  WHERE "fcm_token" IS NOT NULL AND btrim("fcm_token") <> ''
  ORDER BY "fcm_token", "created_at" DESC, "id" DESC
) legacy
ON CONFLICT ("token") DO UPDATE SET
  "parent_id" = EXCLUDED."parent_id",
  "updated_at" = now();

ALTER TABLE "parents" DROP COLUMN IF EXISTS "fcm_token";
