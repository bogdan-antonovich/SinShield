import type { Article } from './types'

export function parseArticleMarkdown(source: string, sourceName?: string): Article
export function parseArticleCollection(sources: Record<string, string>): Article[]
