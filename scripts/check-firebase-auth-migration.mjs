import 'dotenv/config';
import pg from 'pg';
import { applicationDefault, cert, initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';

const url = process.env.DATABASE_URL;
if (!url) throw new Error('DATABASE_URL not set');

function serviceAccount() {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON ?? process.env.FCM_SERVICE_ACCOUNT_JSON;
  if (!raw) return null;
  let value;
  try { value = JSON.parse(raw); }
  catch { value = JSON.parse(Buffer.from(raw, 'base64').toString('utf8')); }
  if (typeof value.private_key === 'string') value.private_key = value.private_key.replace(/\\n/g, '\n');
  return value;
}

const client = new pg.Client({ connectionString: url });
await client.connect();
const duplicateEmails = await client.query(`
  SELECT lower(btrim(email)) AS email, array_agg(id ORDER BY id) AS parent_ids
  FROM parents
  GROUP BY lower(btrim(email))
  HAVING count(*) > 1`);
const duplicateGoogle = await client.query(`
  SELECT google_sub, array_agg(id ORDER BY id) AS parent_ids
  FROM parents
  WHERE google_sub IS NOT NULL
  GROUP BY google_sub
  HAVING count(*) > 1`);
const parents = await client.query(`
  SELECT id, lower(btrim(email)) AS email, google_sub
  FROM parents
  ORDER BY id`);
await client.end();

let failed = false;
for (const row of duplicateEmails.rows) {
  failed = true;
  console.error('CONFLICT duplicate_email', row.email, row.parent_ids);
}
for (const row of duplicateGoogle.rows) {
  failed = true;
  console.error('CONFLICT duplicate_google_identity', row.google_sub, row.parent_ids);
}

if (process.argv.includes('--database-only')) {
  console.log(`Checked ${parents.rowCount} Neon parents (Firebase checks skipped).`);
  if (failed) process.exit(1);
  process.exit(0);
}

const account = serviceAccount();
const projectId = process.env.FIREBASE_PROJECT_ID ?? account?.project_id;
if (!projectId) throw new Error('FIREBASE_PROJECT_ID or Firebase service-account JSON is required');
const app = initializeApp({
  projectId,
  credential: account ? cert(account) : applicationDefault(),
});
const auth = getAuth(app);

async function find(method, value) {
  try { return await method(value); }
  catch (error) {
    if (error?.code === 'auth/user-not-found') return null;
    throw error;
  }
}

for (const parent of parents.rows) {
  const [byUid, byEmail] = await Promise.all([
    find((uid) => auth.getUser(uid), parent.id),
    find((email) => auth.getUserByEmail(email), parent.email),
  ]);
  const byGoogle = parent.google_sub
    ? (await auth.getUsers([{ providerId: 'google.com', providerUid: parent.google_sub }])).users[0] ?? null
    : null;
  if (byUid && byUid.email?.trim().toLowerCase() !== parent.email) {
    failed = true;
    console.error('CONFLICT uid_email_mismatch', parent.id, parent.email, byUid.email);
  }
  if (byEmail && byEmail.uid !== parent.id) {
    failed = true;
    console.error('CONFLICT email_owned_by_other_uid', parent.id, parent.email, byEmail.uid);
  }
  if (parent.google_sub) {
    if (byGoogle && byGoogle.uid !== parent.id) {
      failed = true;
      console.error('CONFLICT google_identity_owned_by_other_uid', parent.id, parent.google_sub, byGoogle.uid);
    }
    const linked = byUid?.providerData.find((provider) => provider.providerId === 'google.com');
    if (linked && linked.uid !== parent.google_sub) {
      failed = true;
      console.error('CONFLICT google_identity_mismatch', parent.id, parent.google_sub, linked.uid);
    }
  }
}

console.log(`Checked ${parents.rowCount} Neon parents against Firebase project ${projectId}.`);
if (failed) process.exit(1);
console.log('Firebase authentication migration preflight passed.');
