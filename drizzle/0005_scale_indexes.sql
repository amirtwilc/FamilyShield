-- Scale / DBA pass: keyset-pagination + hot-path indexes.

-- Messages: keyset pagination orders by (child_id, created_at, id); the unread
-- partial index serves the conversation-list count and the mark-read update.
CREATE INDEX IF NOT EXISTS "messages_child_time_id_idx" ON "messages" ("child_id","created_at","id");
DROP INDEX IF EXISTS "messages_child_time_idx";
CREATE INDEX IF NOT EXISTS "messages_unread_idx" ON "messages" ("child_id") WHERE "read_at" IS NULL;
--> statement-breakpoint
-- Alerts: cursor pagination orders by (child_id, created_at, id).
CREATE INDEX IF NOT EXISTS "alerts_child_time_id_idx" ON "alerts" ("child_id","created_at","id");
DROP INDEX IF EXISTS "alerts_child_time_idx";
--> statement-breakpoint
-- Pairing codes: only unconsumed codes are ever claimed; index just those.
CREATE INDEX IF NOT EXISTS "pairing_codes_active_idx" ON "pairing_codes" ("code") WHERE "consumed_at" IS NULL;
DROP INDEX IF EXISTS "pairing_active_idx";
