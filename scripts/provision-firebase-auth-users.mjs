import 'dotenv/config';
import pg from 'pg';
import { applicationDefault, cert, initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';

const apply = process.argv.includes('--apply');
if (!apply) console.log('DRY RUN: no Firebase or Neon records will be changed. Pass --apply after reviewing.');

const url = process.env.DATABASE_URL;
if (!url) throw new Error('DATABASE_URL not set');
const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON ?? process.env.FCM_SERVICE_ACCOUNT_JSON;
let account = null;
if (raw) {
  try { account = JSON.parse(raw); }
  catch { account = JSON.parse(Buffer.from(raw, 'base64').toString('utf8')); }
  if (typeof account.private_key === 'string') account.private_key = account.private_key.replace(/\\n/g, '\n');
}
const projectId = process.env.FIREBASE_PROJECT_ID ?? account?.project_id;
if (!projectId) throw new Error('FIREBASE_PROJECT_ID or Firebase service-account JSON is required');

const app = initializeApp({
  projectId,
  credential: account ? cert(account) : applicationDefault(),
});
const auth = getAuth(app);
const client = new pg.Client({ connectionString: url });
await client.connect();

const duplicateCheck = await client.query(`
  SELECT 'email' AS kind, lower(btrim(email)) AS value
  FROM parents GROUP BY lower(btrim(email)) HAVING count(*) > 1
  UNION ALL
  SELECT 'google_sub' AS kind, google_sub AS value
  FROM parents WHERE google_sub IS NOT NULL GROUP BY google_sub HAVING count(*) > 1`);
if (duplicateCheck.rowCount) {
  await client.end();
  throw new Error(`Duplicate ${duplicateCheck.rows[0].kind} ${duplicateCheck.rows[0].value}. Run npm run auth:preflight and resolve every conflict.`);
}

const result = await client.query(`
  SELECT id, lower(btrim(email)) AS email, google_sub, firebase_uid
  FROM parents ORDER BY id`);

async function lookup(method, value) {
  try { return await method(value); }
  catch (error) {
    if (error?.code === 'auth/user-not-found') return null;
    throw error;
  }
}

let created = 0;
let existing = 0;
for (const parent of result.rows) {
  if (parent.firebase_uid && parent.firebase_uid !== parent.id) {
    throw new Error(`Parent ${parent.id} maps to unexpected Firebase UID ${parent.firebase_uid}`);
  }
  const [byUid, byEmail] = await Promise.all([
    lookup((uid) => auth.getUser(uid), parent.id),
    lookup((email) => auth.getUserByEmail(email), parent.email),
  ]);
  const byGoogle = parent.google_sub
    ? (await auth.getUsers([{ providerId: 'google.com', providerUid: parent.google_sub }])).users[0] ?? null
    : null;
  if (byEmail && byEmail.uid !== parent.id) {
    throw new Error(`Email ${parent.email} is already owned by Firebase UID ${byEmail.uid}`);
  }
  if (byUid && byUid.email?.trim().toLowerCase() !== parent.email) {
    throw new Error(`Firebase UID ${parent.id} has mismatched email ${byUid.email}`);
  }
  if (byGoogle && byGoogle.uid !== parent.id) {
    throw new Error(`Google identity ${parent.google_sub} is already owned by Firebase UID ${byGoogle.uid}`);
  }
  const linkedGoogle = byUid?.providerData.find((provider) => provider.providerId === 'google.com');
  if (linkedGoogle && linkedGoogle.uid !== parent.google_sub) {
    throw new Error(`Firebase UID ${parent.id} has a mismatched Google identity`);
  }

  console.log(byUid ? 'EXISTS' : 'CREATE', parent.id, parent.email, parent.google_sub ? 'google' : 'password');
  if (!apply) continue;

  if (!byUid) {
    const importResult = await auth.importUsers([{
      uid: parent.id,
      email: parent.email,
      emailVerified: Boolean(parent.google_sub),
      providerData: parent.google_sub ? [{
        uid: parent.google_sub,
        providerId: 'google.com',
        email: parent.email,
      }] : [],
    }]);
    if (importResult.failureCount) {
      throw importResult.errors[0]?.error ?? new Error(`Failed to import ${parent.id}`);
    }
    created++;
  } else {
    existing++;
  }

  await client.query(
    `UPDATE parents
     SET firebase_uid = $1
     WHERE id = $1 AND (firebase_uid IS NULL OR firebase_uid = $1)`,
    [parent.id],
  );
}

await client.end();
console.log(apply
  ? `Provisioning complete: ${created} created, ${existing} already existed.`
  : `Dry run complete: ${result.rowCount} parent accounts checked.`);
