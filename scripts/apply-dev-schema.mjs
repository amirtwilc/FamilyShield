// One-off: apply the full schema to the dev database (DATABASE_URL).
// Mirrors test/helpers/db.ts resetDb but targets the dev DB so `npm run dev`
// has the tables the routes expect. Safe to re-run (resets public schema).
import 'dotenv/config';
import { readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import pg from 'pg';

const url = process.env.DATABASE_URL;
if (!url) throw new Error('DATABASE_URL not set');

const client = new pg.Client({ connectionString: url });
await client.connect();

await client.query('DROP SCHEMA public CASCADE; CREATE SCHEMA public;');
await client.query('CREATE EXTENSION IF NOT EXISTS postgis;');

const dir = resolve(process.cwd(), 'drizzle');
const files = readdirSync(dir).filter((f) => f.endsWith('.sql')).sort();
for (const f of files) {
  const body = readFileSync(resolve(dir, f), 'utf8');
  await client.query(body);
  console.log('applied', f);
}

await client.end();
console.log('dev schema ready');
