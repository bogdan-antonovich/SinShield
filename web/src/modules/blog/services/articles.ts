import type { Article } from '@/modules/blog/types'

export const BLOG_PAGE_SIZE = 6

const articleRecords = import.meta.glob<Article>('@/modules/blog/content/*.md', {
  eager: true,
  query: '?article',
  import: 'default',
})
const articles = Object.values(articleRecords).sort((left, right) => {
  if (Boolean(left.featured) !== Boolean(right.featured)) return left.featured ? -1 : 1
  return Date.parse(right.publishedAt) - Date.parse(left.publishedAt)
})

export function getArticles(): Article[] {
  return articles
}

export function getArticleBySlug(slug: string): Article | undefined {
  return articles.find((article) => article.slug === slug)
}

export function getArticlePath(article: Article): string {
  return `/blog/${article.slug}`
}

const articleMetaTitles: Record<string, string> = {
  'benefits-of-quitting-porn': 'Benefits of Quitting Porn: 8 Realistic Changes',
  'best-accountability-apps-to-quit-porn': 'Best Accountability Apps to Quit Porn (2026)',
  'countries-where-porn-is-illegal': 'Where Is Porn Illegal? Country Laws Explained',
  'does-porn-lower-your-iq': 'Does Porn Lower Your IQ? Research Explained',
  'how-to-break-the-scroll-trigger-loop': 'How to Break the Scroll–Trigger Loop',
  'porn-on-x': 'Porn on X: Rules, Filters, and How to Block It',
}

export function getArticleMetaTitle(article: Article): string {
  return articleMetaTitles[article.slug] ?? article.title
}

export function getBlogPageCount(articleCount: number): number {
  return Math.max(1, Math.ceil(articleCount / BLOG_PAGE_SIZE))
}

export function normalizeBlogPage(value: unknown, totalPages: number): number {
  const rawValue = Array.isArray(value) ? value[0] : value
  const page = typeof rawValue === 'string' && /^\d+$/.test(rawValue) ? Number(rawValue) : 1

  return Math.min(Math.max(page, 1), Math.max(totalPages, 1))
}

export function getBlogPage(allArticles: Article[], page: number): Article[] {
  const start = (page - 1) * BLOG_PAGE_SIZE
  return allArticles.slice(start, start + BLOG_PAGE_SIZE)
}

export function formatArticleDate(value: string): string {
  return new Intl.DateTimeFormat('en', {
    month: 'long',
    day: 'numeric',
    year: 'numeric',
    timeZone: 'UTC',
  }).format(new Date(`${value}T00:00:00Z`))
}
