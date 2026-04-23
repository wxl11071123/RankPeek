import { defineConfig } from 'vite'
import { resolve } from 'path'

export default defineConfig({
  build: {
    outDir: resolve(__dirname, 'dist/main'),
    emptyOutDir: true,
    lib: {
      entry: resolve(__dirname, 'src/main/main.ts'),
      formats: ['cjs'],
      fileName: () => 'main.js'
    },
    rollupOptions: {
      external: ['electron', 'child_process', 'path', 'fs', 'http', 'https', 'url', 'os', 'stream', 'events', 'util', 'buffer', 'crypto', 'net', 'tls', 'zlib', 'querystring'],
      output: {
        entryFileNames: 'main.js'
      }
    },
    minify: false
  }
})
