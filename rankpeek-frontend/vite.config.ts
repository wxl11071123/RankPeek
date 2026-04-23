import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  plugins: [vue()],
  base: './',
  root: 'src/renderer',
  publicDir: resolve(__dirname, 'public'),
  define: {
    global: 'globalThis'
  },
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src/renderer')
    }
  },
  build: {
    outDir: resolve(__dirname, 'dist/renderer'),
    emptyOutDir: true,
    rollupOptions: {
      output: {
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'stomp': ['@stomp/stompjs', 'sockjs-client']
        }
      }
    }
  },
  server: {
    port: 5173,
    strictPort: true
  }
})
