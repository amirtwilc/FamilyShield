ALTER TABLE "children" ADD COLUMN IF NOT EXISTS "avatar" text NOT NULL DEFAULT 'fox';

ALTER TYPE "alert_type" ADD VALUE IF NOT EXISTS 'child_unpaired';
