INSERT INTO "subscription_tiers" (
  "code",
  "name",
  "location_retention_days",
  "max_children",
  "is_active"
)
VALUES ('admin', 'Admin', 2, 5, true)
ON CONFLICT ("code") DO UPDATE SET
  "name" = EXCLUDED."name",
  "location_retention_days" = EXCLUDED."location_retention_days",
  "max_children" = EXCLUDED."max_children",
  "is_active" = EXCLUDED."is_active",
  "updated_at" = now();
