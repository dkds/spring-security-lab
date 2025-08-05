import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import path from 'path';
import { defineConfig, loadEnv } from 'vite';
import envCompatible from 'vite-plugin-env-compatible';
import { createHtmlPlugin } from 'vite-plugin-html';

const ENV_PREFIX = 'REACT_APP_';

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, 'env', ENV_PREFIX);

  return {
    plugins: [
      react(),
      tailwindcss(),
      envCompatible({ prefix: ENV_PREFIX }),
      createHtmlPlugin({
        inject: {
          data: {
            env: {
              NODE_ENV: process.env.NODE_ENV,
              REACT_APP_CLIENT_TOKEN: process.env.REACT_APP_CLIENT_TOKEN,
              REACT_APP_ENV: process.env.REACT_APP_ENV,
            },
          },
        },
        minify: true,
      }),
    ],
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: ['./src/setupTests.js'],
    },
    resolve: {
      alias: {
        '~': path.resolve(__dirname, 'src'),
      },
    },
    server: {
      port: 3000,
      open: env.SERVER_OPEN_BROWSER === 'true',
      proxy: {
        // '/as-api/logout': {
        //   target: 'http://localhost:8080', // Backend server
        //   changeOrigin: true, // Ensure the request appears to come from the frontend server
        //   rewrite: (path) => path.replace(/^\/as-api/, ''), // Optional: Remove '/as-api' prefix
        // },
        '/as-api': {
          target: 'http://localhost:8080', // Backend server
          changeOrigin: true, // Ensure the request appears to come from the frontend server
        },
        '/oauth': {
          target: 'http://localhost:8080', // Backend server
          changeOrigin: true, // Ensure the request appears to come from the frontend server
        },
        '/sso': {
          target: 'http://localhost:8080', // Backend server
          changeOrigin: true, // Ensure the request appears to come from the frontend server
        },
      },
    },
    build: {
      outDir: 'build',
    },
    envPrefix: 'APP_',
  };
});
