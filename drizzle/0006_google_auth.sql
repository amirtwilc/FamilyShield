-- Google ("Gmail") sign-in: parents may have no local password, and carry a
-- Google subject id.
ALTER TABLE "parents" ALTER COLUMN "password_hash" DROP NOT NULL;
--> statement-breakpoint
ALTER TABLE "parents" ADD COLUMN IF NOT EXISTS "google_sub" text;
--> statement-breakpoint
CREATE UNIQUE INDEX IF NOT EXISTS "parents_google_sub_idx" ON "parents" ("google_sub") WHERE "google_sub" IS NOT NULL;
