import { readdirSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { sql } from 'drizzle-orm';
import { db } from '@/db/client';

/** Drops & recreates the full schema for a clean test database. */
export async function resetDb(): Promise<void> {
  await db.execute(sql.raw('DROP SCHEMA public CASCADE; CREATE SCHEMA public;'));
  // postgis must exist before the generated baseline migration runs, since it
  // creates geometry(Point,4326) columns that require the extension's types.
  await db.execute(sql.raw('CREATE EXTENSION IF NOT EXISTS postgis;'));

  // Apply every *.sql file in drizzle/, sorted ascending, so the generated
  // baseline (0000_*) runs before the hand-written partition override
  // (0001_locations_partition.sql). Filenames are not hardcoded because
  // drizzle-kit suffixes baselines with a random name.
  const dir = resolve(process.cwd(), 'drizzle');
  const files = readdirSync(dir)
    .filter((f) => f.endsWith('.sql'))
    .sort();

  for (const f of files) {
    const body = readFileSync(resolve(dir, f), 'utf8');
    await db.execute(sql.raw(body));
  }
}

/** Runs `fn` inside a transaction that is always rolled back; useful for isolated test cases. */
export async function withRollback(fn: (tx: typeof db) => Promise<void>): Promise<void> {
  try {
    await db.transaction(async (tx) => {
      await fn(tx as unknown as typeof db);
      throw new RollbackSignal();
    });
  } catch (err) {
    if (!(err instanceof RollbackSignal)) throw err;
  }
}

class RollbackSignal extends Error {}
