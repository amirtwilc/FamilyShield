CREATE INDEX IF NOT EXISTS "messages_created_at_idx" ON "messages" ("created_at");
CREATE INDEX IF NOT EXISTS "messages_unread_parent_child_idx"
  ON "messages" ("parent_id", "child_id", "sender")
  WHERE "read_at" IS NULL;
