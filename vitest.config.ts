import 'dotenv/config';
import { defineConfig } from 'vitest/config';
import { resolve } from 'node:path';

process.env.MESSAGE_ENCRYPTION_KEY ??= Buffer.alloc(32, 7).toString('base64');

export default defineConfig({
  test: {
    environment: 'node',
    globals: false,
    fileParallelism: false,
    exclude: ['node_modules/**'],
  },
  resolve: { alias: { '@': resolve(__dirname, 'src') } },
});
