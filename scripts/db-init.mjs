// Idempotent schema bootstrap for the backend container. Waits for the DB, then
// applies the full schema (postgis + every drizzle/*.sql) ONLY if it isn't there
// yet — so it is safe to run on every container start and never wipes data.
import 'dotenv/config';
import { readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import pg from 'pg';

const url = process.env.DATABASE_URL;
if (!url) { console.error('db-init: DATABASE_URL not set'); process.exit(1); }

async function connectWithRetry(attempts = 30) {
  for (let i = 0; i < attempts; i++) {
    const client = new pg.Client({ connectionString: url });
    try { await client.connect(); return client; }
    catch { await client.end().catch(() => {}); console.log(`db-init: waiting for database… (${i + 1})`); await new Promise((r) => setTimeout(r, 2000)); }
  }
  throw new Error('db-init: database not reachable');
}

const client = await connectWithRetry();
const { rows } = await client.query("SELECT to_regclass('public.parents') AS t");
if (rows[0].t) {
  console.log('db-init: schema already present — nothing to do');
} else {
  await client.query('CREATE EXTENSION IF NOT EXISTS postgis;');
  await client.query(`
    CREATE TABLE IF NOT EXISTS familyshield_migrations (
      filename text PRIMARY KEY,
      applied_at timestamptz NOT NULL DEFAULT now()
    )`);
  const dir = resolve(process.cwd(), 'drizzle');
  for (const f of readdirSync(dir).filter((f) => f.endsWith('.sql')).sort()) {
    await client.query('BEGIN');
    try {
      await client.query(readFileSync(resolve(dir, f), 'utf8'));
      await client.query('INSERT INTO familyshield_migrations (filename) VALUES ($1)', [f]);
      await client.query('COMMIT');
      console.log('db-init: applied', f);
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    }
  }
  console.log('db-init: schema initialized');
}
await client.end();
