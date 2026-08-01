import 'dotenv/config';
import { pathToFileURL } from 'node:url';
import pg from 'pg';

const ALLOWED_TIERS = new Set(['free', 'admin']);

export function parseTierAssignmentArgs(argv) {
  const values = new Map();
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument !== '--email' && argument !== '--tier') {
      throw new Error(`Unknown argument: ${argument}`);
    }
    const value = argv[index + 1]?.trim();
    if (!value || value.startsWith('--')) throw new Error(`Missing value for ${argument}`);
    values.set(argument, value);
    index += 1;
  }

  const email = values.get('--email')?.trim().toLowerCase();
  const tier = values.get('--tier')?.trim().toLowerCase();
  if (!email) throw new Error('Usage: npm run tier:set -- --email <email> --tier <free|admin>');
  if (!tier || !ALLOWED_TIERS.has(tier)) throw new Error('Tier must be either free or admin');
  return { email, tier };
}

export async function assignParentTier(client, { email, tier }) {
  const tierResult = await client.query(
    'SELECT code FROM subscription_tiers WHERE code = $1 AND is_active = true',
    [tier],
  );
  if (tierResult.rowCount !== 1) throw new Error(`Active subscription tier not found: ${tier}`);

  const parentResult = await client.query(
    'SELECT id, email, tier_code FROM parents WHERE lower(btrim(email)) = $1',
    [email.trim().toLowerCase()],
  );
  if (parentResult.rowCount !== 1) {
    throw new Error(`Expected exactly one parent for ${email}; found ${parentResult.rowCount}`);
  }

  const parent = parentResult.rows[0];
  await client.query('UPDATE parents SET tier_code = $1 WHERE id = $2', [tier, parent.id]);
  return { email: parent.email, previousTier: parent.tier_code, tier };
}

async function main() {
  const databaseUrl = process.env.DATABASE_URL;
  if (!databaseUrl) throw new Error('DATABASE_URL not set');
  const assignment = parseTierAssignmentArgs(process.argv.slice(2));
  const client = new pg.Client({ connectionString: databaseUrl });
  await client.connect();
  try {
    const result = await assignParentTier(client, assignment);
    console.log(`${result.email}: ${result.previousTier} -> ${result.tier}`);
  } finally {
    await client.end();
  }
}

const executedPath = process.argv[1] ? pathToFileURL(process.argv[1]).href : null;
if (executedPath === import.meta.url) {
  main().catch((error) => {
    console.error(error instanceof Error ? error.message : error);
    process.exitCode = 1;
  });
}
