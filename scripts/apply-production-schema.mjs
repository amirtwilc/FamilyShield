// Production-safe schema bootstrap for Neon/Vercel.
//
// Applies every drizzle/*.sql file exactly once using a simple migrations ledger.
// It refuses to silently baseline a non-empty DB with no ledger because this repo
// contains hand-written SQL (notably the partition override) that must not be
// replayed against existing production data.
import 'dotenv/config';
import { readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import pg from 'pg';

const url = process.env.DATABASE_URL;
if (!url) throw new Error('DATABASE_URL not set');

const client = new pg.Client({ connectionString: url });
await client.connect();

await client.query('CREATE EXTENSION IF NOT EXISTS postgis;');
await client.query(`
  CREATE TABLE IF NOT EXISTS familyshield_migrations (
    filename text PRIMARY KEY,
    applied_at timestamptz NOT NULL DEFAULT now()
  )`);

const existing = await client.query("SELECT to_regclass('public.parents') AS parents");
const applied = await client.query('SELECT filename FROM familyshield_migrations');
if (existing.rows[0].parents && applied.rowCount === 0 && process.env.ALLOW_BASELINE_EXISTING !== '1') {
  await client.end();
  throw new Error('Existing schema has no familyshield_migrations ledger. Set ALLOW_BASELINE_EXISTING=1 only after verifying migrations manually.');
}

const done = new Set(applied.rows.map((r) => r.filename));
const dir = resolve(process.cwd(), 'drizzle');
const files = readdirSync(dir).filter((f) => f.endsWith('.sql')).sort();

for (const file of files) {
  if (done.has(file)) continue;
  await client.query('BEGIN');
  try {
    await client.query(readFileSync(resolve(dir, file), 'utf8'));
    await client.query('INSERT INTO familyshield_migrations (filename) VALUES ($1)', [file]);
    await client.query('COMMIT');
    console.log('applied', file);
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  }
}

await client.end();
console.log('production schema ready');
