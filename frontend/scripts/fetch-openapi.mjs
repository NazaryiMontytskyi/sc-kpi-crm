// Downloads the OpenAPI document from a running backend (dev/test profile exposes /v3/api-docs)
// and stores it as the pinned spec used by `npm run api:generate`.
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const url = process.env.OPENAPI_URL ?? 'http://localhost:8080/v3/api-docs';
const target = resolve(dirname(fileURLToPath(import.meta.url)), '../openapi/openapi.json');

const response = await fetch(url);
if (!response.ok) {
  console.error(`Failed to fetch ${url}: HTTP ${response.status}`);
  process.exit(1);
}
const spec = await response.json();
mkdirSync(dirname(target), { recursive: true });
writeFileSync(target, JSON.stringify(spec, null, 2) + '\n');
console.log(`Saved ${url} -> ${target}`);
