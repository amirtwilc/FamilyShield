import 'dotenv/config';
import pg from 'pg';

const url = process.env.DATABASE_URL;
if (!url) throw new Error('DATABASE_URL not set');

const client = new pg.Client({ connectionString: url });
await client.connect();

const checks = [
  ['postgis', 'SELECT postgis_version() AS ok'],
  ['subscription_tiers', "SELECT to_regclass('public.subscription_tiers') AS ok"],
  ['free_tier', "SELECT code AS ok FROM subscription_tiers WHERE code = 'free' AND location_retention_days = 2 AND max_children = 5"],
  ['parent_tier_code', "SELECT column_name AS ok FROM information_schema.columns WHERE table_name = 'parents' AND column_name = 'tier_code'"],
  ['parent_firebase_uid', "SELECT column_name AS ok FROM information_schema.columns WHERE table_name = 'parents' AND column_name = 'firebase_uid'"],
  ['normalized_parent_email', "SELECT to_regclass('public.parents_email_normalized_unique_idx') AS ok"],
  ['child_phone_number', "SELECT column_name AS ok FROM information_schema.columns WHERE table_name = 'children' AND column_name = 'phone_number'"],
  ['child_parent_links', "SELECT to_regclass('public.child_parent_links') AS ok"],
  ['equal_guardians_no_child_owner', "SELECT NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'children' AND column_name = 'parent_id') AS ok"],
  ['equal_guardians_no_role', "SELECT NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'child_parent_links' AND column_name = 'role') AS ok"],
  ['unique_active_pairing_codes', "SELECT to_regclass('public.pairing_codes_active_unique_idx') AS ok"],
  ['safe_zone_observation_order', "SELECT column_name AS ok FROM information_schema.columns WHERE table_name = 'safe_zone_states' AND column_name = 'last_observed_at'"],
  ['single_active_sos', "SELECT to_regclass('public.sos_events_one_active_child_idx') AS ok"],
  ['shared_rate_limits', "SELECT to_regclass('public.rate_limit_buckets') AS ok"],
  ['message_parent_id', "SELECT column_name AS ok FROM information_schema.columns WHERE table_name = 'messages' AND column_name = 'parent_id'"],
  ['locations_partitioned', "SELECT relkind AS ok FROM pg_class WHERE relname = 'locations' AND relkind = 'p'"],
];

let failed = false;
for (const [name, query] of checks) {
  const result = await client.query(query);
  const ok = Boolean(result.rows[0]?.ok);
  console.log(`${ok ? 'OK' : 'FAIL'} ${name}`);
  failed = failed || !ok;
}

await client.end();
if (failed) process.exit(1);
