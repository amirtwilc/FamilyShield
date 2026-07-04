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
  const dir = resolve(process.cwd(), 'drizzle');
  for (const f of readdirSync(dir).filter((f) => f.endsWith('.sql')).sort()) {
    await client.query(readFileSync(resolve(dir, f), 'utf8'));
    console.log('db-init: applied', f);
  }
  console.log('db-init: schema initialized');
}
await client.end();
