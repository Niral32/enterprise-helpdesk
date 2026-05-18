import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: false,
    proxy: {
      '/api': {
        target: 'http://localhost:8000',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '/api'),
      },
    },
  },
  build: {
    target: 'ES2020',
    outDir: 'dist',
    // esbuild is bundled with Vite (no extra install) and ~20-40x faster
    // than terser. Switch back to 'terser' only if you need its slightly
    // smaller output AND add `terser` to package.json devDependencies.
    minify: 'esbuild',
    sourcemap: false,
  },
})
