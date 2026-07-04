// Generates an image with Nano Banana (Gemini 2.5 Flash Image) via Vertex AI and
// writes it to a PNG. Uses the local gcloud credentials for an access token held
// only in memory (never written to disk).
//
//   node scripts/gen-image.mjs "<prompt>" <output.png>
//
import { execSync } from 'node:child_process';
import { writeFileSync } from 'node:fs';

const prompt = process.argv[2];
const out = process.argv[3];
if (!prompt || !out) { console.error('usage: gen-image.mjs "<prompt>" <output.png>'); process.exit(1); }

const PROJECT = process.env.GCP_PROJECT || execSync('gcloud config get-value project', { encoding: 'utf8' }).trim();
const token = execSync('gcloud auth print-access-token', { encoding: 'utf8' }).trim();

const models = ['gemini-2.5-flash-image', 'gemini-2.5-flash-image-preview'];
const locations = ['global', 'us-central1'];

async function tryGen(location, model) {
  const host = location === 'global' ? 'aiplatform.googleapis.com' : `${location}-aiplatform.googleapis.com`;
  const url = `https://${host}/v1/projects/${PROJECT}/locations/${location}/publishers/google/models/${model}:generateContent`;
  const res = await fetch(url, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      contents: [{ role: 'user', parts: [{ text: prompt }] }],
      generationConfig: { responseModalities: ['TEXT', 'IMAGE'] },
    }),
  });
  const text = await res.text();
  if (!res.ok) return { ok: false, status: res.status, msg: text.slice(0, 300) };
  const data = JSON.parse(text);
  const parts = data?.candidates?.[0]?.content?.parts || [];
  const img = parts.find((p) => p.inlineData?.data);
  if (!img) return { ok: false, status: res.status, msg: 'no image part: ' + text.slice(0, 200) };
  writeFileSync(out, Buffer.from(img.inlineData.data, 'base64'));
  return { ok: true, location, model };
}

for (const location of locations) {
  for (const model of models) {
    try {
      const r = await tryGen(location, model);
      if (r.ok) { console.log(`OK ${out} via ${r.location}/${r.model}`); process.exit(0); }
      console.error(`fail ${location}/${model}: ${r.status} ${r.msg}`);
    } catch (e) { console.error(`err ${location}/${model}: ${e.message}`); }
  }
}
console.error('all attempts failed');
process.exit(2);
