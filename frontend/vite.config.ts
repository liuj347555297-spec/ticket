import { fileURLToPath, URL } from 'node:url'
import { defineConfig, type Plugin } from 'vite'
import type { OutputChunk } from 'rollup'
import vue from '@vitejs/plugin-vue'

const chunkBudgetBytes: Record<string, number> = {
  'vendor-framework': 260 * 1024,
  'vendor-editor': 700 * 1024,
  'vendor-ui': 1_050 * 1024,
  'vendor-bpmn': 950 * 1024,
  default: 450 * 1024,
}

function enforceChunkBudget(): Plugin {
  return {
    name: 'servicehub-enforce-chunk-budget',
    apply: 'build',
    generateBundle(_, bundle) {
      const oversized = Object.values(bundle)
        .filter((item): item is OutputChunk => item.type === 'chunk')
        .map((chunk) => ({ chunk, budget: chunkBudgetBytes[chunk.name] ?? chunkBudgetBytes.default }))
        .filter(({ chunk, budget }) => chunk.code.length > budget)
        .map(({ chunk, budget }) => `${chunk.fileName}: ${chunk.code.length} bytes (budget ${budget})`)
      if (oversized.length > 0) {
        this.error(`Production chunk budget exceeded:\n${oversized.join('\n')}`)
      }
    },
  }
}

export default defineConfig({
  plugins: [vue(), enforceChunkBudget()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  build: {
    sourcemap: false,
    chunkSizeWarningLimit: 1050,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('/node_modules/')) return undefined
          if (id.includes('bpmn-js') || id.includes('diagram-js') || id.includes('bpmn-moddle') || id.includes('/moddle')) return 'vendor-bpmn'
          if (id.includes('@tiptap/') || id.includes('prosemirror')) return 'vendor-editor'
          if (id.includes('element-plus') || id.includes('@element-plus')) return 'vendor-ui'
          if (id.includes('/vue/') || id.includes('vue-router') || id.includes('pinia')) return 'vendor-framework'
          return 'vendor-other'
        },
      },
    },
  },
  server: {
    host: '127.0.0.1',
    port: 1525,
    strictPort: true,
    // Browser requests stay same-origin in local development. This avoids weakening the
    // backend CORS policy and prevents Vite's SPA fallback from swallowing /api/v1 calls.
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
})
