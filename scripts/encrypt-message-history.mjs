import 'dotenv/config';
import { createCipheriv, randomBytes } from 'node:crypto';
import pg from 'pg';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) throw new Error('DATABASE_URL not set');
const key = Buffer.from(process.env.MESSAGE_ENCRYPTION_KEY ?? '', 'base64');
if (key.length !== 32) throw new Error('MESSAGE_ENCRYPTION_KEY must be a base64-encoded 32-byte key');

function encrypt(plaintext) {
  const iv = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', key, iv);
  const ciphertext = Buffer.concat([cipher.update(plaintext, 'utf8'), cipher.final()]);
  return ['enc:v1', iv.toString('base64url'), ciphertext.toString('base64url'), cipher.getAuthTag().toString('base64url')].join(':');
}

const client = new pg.Client({ connectionString: databaseUrl });
await client.connect();
let migrated = 0;
try {
  while (true) {
    const result = await client.query(`
      SELECT id, body FROM messages
      WHERE body NOT LIKE 'enc:v1:%'
      ORDER BY created_at, id
      LIMIT 500
    `);
    if (result.rows.length === 0) break;
    await client.query('BEGIN');
    try {
      for (const row of result.rows) {
        await client.query('UPDATE messages SET body = $1 WHERE id = $2', [encrypt(row.body), row.id]);
      }
      await client.query('COMMIT');
      migrated += result.rows.length;
      console.log(`encrypted ${migrated} messages`);
    } catch (error) {
      await client.query('ROLLBACK');
      throw error;
    }
  }
} finally {
  await client.end();
}
console.log(`message encryption complete (${migrated} migrated)`);
