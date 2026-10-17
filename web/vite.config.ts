import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  build: { rollupOptions: { output: { manualChunks(id) { if(id.includes('/node_modules/zrender/')) return 'chart-renderer' } } } },
  server: {
    host: '0.0.0.0',
    port: 5173,
    strictPort: true,
    proxy: { '/api': { target: process.env.API_PROXY_TARGET ?? 'http://127.0.0.1:8080', changeOrigin: true } },
  },
})
