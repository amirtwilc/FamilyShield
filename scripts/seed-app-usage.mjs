// Seeds 7 days of per-app screen-time for every child so the parent App Usage
// screen shows realistic data. Idempotent: clears app_usage first, then inserts.
//
//   node scripts/seed-app-usage.mjs
//
// Connects directly to Postgres (the kid simulator can't read UsageStatsManager).
import 'dotenv/config';
import pg from 'pg';

const url = process.env.SEED_DB_URL
  || process.env.DATABASE_URL?.replace('@db:5432', '@localhost:5433')
  || 'postgres://familyshield:familyshield@localhost:5433/familyshield';

const CATALOG = [
  ['YouTube', 'Entertainment'], ['Roblox', 'Games'], ['WhatsApp', 'Social'],
  ['TikTok', 'Entertainment'], ['Spotify', 'Music'], ['Chrome', 'Productivity'],
  ['Instagram', 'Social'], ['Minecraft', 'Games'],
];

// Deterministic per-child pseudo-random so re-seeding looks stable.
function rng(seed) {
  let s = 0; for (const ch of seed) s = (s * 31 + ch.charCodeAt(0)) >>> 0;
  return () => { s = (s * 1664525 + 1013904223) >>> 0; return s / 0xffffffff; };
}

const client = new pg.Client({ connectionString: url });
await client.connect();
const { rows: kids } = await client.query('SELECT id, display_name FROM children ORDER BY display_name');
if (kids.length === 0) { console.log('no children — run the demo seed first'); await client.end(); process.exit(0); }

await client.query('DELETE FROM app_usage');
let total = 0;
for (const kid of kids) {
  const rand = rng(kid.id);
  for (let d = 6; d >= 0; d--) {
    // today (d=0): a fuller breakdown; past days: 3–4 apps.
    const count = d === 0 ? 6 : 3 + Math.floor(rand() * 2);
    const picks = [...CATALOG].sort(() => rand() - 0.5).slice(0, count);
    for (const [app, category] of picks) {
      const minutes = 8 + Math.floor(rand() * (app === 'YouTube' ? 75 : 50));
      await client.query(
        `INSERT INTO app_usage (child_id, app, category, minutes, day)
         VALUES ($1, $2, $3, $4, CURRENT_DATE - $5::int)
         ON CONFLICT (child_id, app, day) DO UPDATE SET minutes = EXCLUDED.minutes`,
        [kid.id, app, category, minutes, d],
      );
      total++;
    }
  }
  console.log(`• seeded app usage for ${kid.display_name}`);
}
console.log(`done — ${total} rows across ${kids.length} children`);
await client.end();
