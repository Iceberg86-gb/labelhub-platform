import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const DEFAULT_API_PROXY_TARGET = 'http://localhost:8080';

function getApiProxyTarget() {
  const processLike = globalThis as typeof globalThis & {
    process?: { env?: Record<string, string | undefined> };
  };

  return processLike.process?.env?.LABELHUB_API_PROXY_TARGET ?? DEFAULT_API_PROXY_TARGET;
}

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: getApiProxyTarget(),
        changeOrigin: true,
      },
    },
  },
});
