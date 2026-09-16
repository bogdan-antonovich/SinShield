import { fileURLToPath, URL } from 'node:url'
import { readFile } from 'node:fs/promises'

import { defineConfig } from 'vite'
import { sites } from '@openai/sites-vite-plugin'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import tailwindcss from '@tailwindcss/vite'
import { parseArticleMarkdown } from './src/modules/blog/markdown.mjs'

function blogMarkdown() {
  return {
    name: 'sinshield-blog-markdown',
    enforce: 'pre' as const,
    async load(id: string) {
      const [filePath, query = ''] = id.split('?', 2)
      if (!filePath.endsWith('.md') || !new URLSearchParams(query).has('article')) return null
      const article = parseArticleMarkdown(await readFile(filePath, 'utf8'), filePath)
      return `export default ${JSON.stringify(article)}`
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    sites(),
    blogMarkdown(),
    vue(),
    vueDevTools(),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})
