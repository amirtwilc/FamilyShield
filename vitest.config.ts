import 'dotenv/config';
import { defineConfig } from 'vitest/config';
import { resolve } from 'node:path';

export default defineConfig({
  test: {
    environment: 'node',
    globals: false,
    fileParallelism: false,
    // E2E (Playwright) lives in test/e2e and is run by `npm run test:e2e`.
    exclude: ['node_modules/**', 'test/e2e/**'],
  },
  resolve: { alias: { '@': resolve(__dirname, 'src') } },
});
