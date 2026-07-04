import { Pool } from 'pg';
import { drizzle } from 'drizzle-orm/node-postgres';
import * as schema from './schema';

const url = process.env.NODE_ENV === 'test'
  ? process.env.TEST_DATABASE_URL!
  : process.env.DATABASE_URL!;

// Pool sized from env so the app never opens more connections than Postgres can
// serve, and statements that run too long are aborted rather than pinning a
// connection (both matter once there are many concurrent requests).
export const pool = new Pool({
  connectionString: url,
  max: Number(process.env.PG_POOL_MAX ?? 10),
  idleTimeoutMillis: Number(process.env.PG_IDLE_MS ?? 30_000),
  connectionTimeoutMillis: Number(process.env.PG_CONNECT_MS ?? 10_000),
  statement_timeout: Number(process.env.PG_STATEMENT_MS ?? 15_000),
});
export const db = drizzle(pool, { schema });
