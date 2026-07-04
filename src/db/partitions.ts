import { sql } from 'drizzle-orm';
import { db } from './client';

/** Creates the monthly partition covering `date` if it does not exist. */
export async function ensureLocationPartition(date: Date): Promise<void> {
  const y = date.getUTCFullYear();
  const m = date.getUTCMonth(); // 0-based
  const start = new Date(Date.UTC(y, m, 1));
  const end = new Date(Date.UTC(y, m + 1, 1));
  const name = `locations_${y}_${String(m + 1).padStart(2, '0')}`;
  const iso = (d: Date) => d.toISOString().slice(0, 10);
  await db.execute(sql.raw(
    `CREATE TABLE IF NOT EXISTS ${name} PARTITION OF locations ` +
    `FOR VALUES FROM ('${iso(start)}') TO ('${iso(end)}');`,
  ));
}
