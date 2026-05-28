import react from '@vitejs/plugin-react';
import { resolve } from 'node:path';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
      '@douyinfe/semi-ui': resolve(__dirname, 'src/features/labeling/formily/__tests__/semiMock.tsx'),
    },
  },
  test: {
    environment: 'jsdom',
    include: ['src/features/labeling/**/*.test.{ts,tsx}', 'src/entities/labeling/**/*.test.ts', 'src/entities/schema/**/*.test.ts'],
    benchmark: {
      include: ['src/features/labeling/__benchmarks__/**/*.bench.tsx'],
      time: 100,
      iterations: 5,
    },
  },
});
