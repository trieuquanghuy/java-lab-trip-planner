/// <reference types="vitest" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      '/api/auth': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/api/trips': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      '/api/favorites': {
        target: 'http://localhost:8082',
        changeOrigin: true,
      },
      '/api/search': {
        target: 'http://localhost:8083',
        changeOrigin: true,
      },
      '/api/destinations': {
        target: 'http://localhost:8083',
        changeOrigin: true,
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
    exclude: ['e2e/**', '**/node_modules/**'],
  },
});
