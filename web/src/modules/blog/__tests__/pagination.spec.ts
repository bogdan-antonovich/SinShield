import { describe, expect, it } from 'vitest'

import {
  BLOG_PAGE_SIZE,
  getBlogPage,
  getBlogPageCount,
  normalizeBlogPage,
} from '@/modules/blog/services/articles'
import type { Article } from '@/modules/blog/types'

function article(slug: string): Article {
  return {
    slug,
    title: slug,
    description: '',
    author: 'SinShield Editorial',
    publishedAt: '2026-01-01',
    readingTime: '1 min read',
    thumbnail: '/thumbnail.jpg',
    thumbnailAlt: '',
    tags: [],
    sections: [],
  }
}

describe('blog pagination', () => {
  const articles = Array.from({ length: BLOG_PAGE_SIZE + 2 }, (_, index) =>
    article(`article-${index + 1}`),
  )

  it('splits articles into fixed-size pages without changing their order', () => {
    expect(getBlogPageCount(articles.length)).toBe(2)
    expect(getBlogPage(articles, 1).map(({ slug }) => slug)).toEqual([
      'article-1',
      'article-2',
      'article-3',
      'article-4',
      'article-5',
      'article-6',
    ])
    expect(getBlogPage(articles, 2).map(({ slug }) => slug)).toEqual(['article-7', 'article-8'])
  })

  it('normalizes missing, malformed, and out-of-range page values', () => {
    expect(normalizeBlogPage(undefined, 2)).toBe(1)
    expect(normalizeBlogPage('nope', 2)).toBe(1)
    expect(normalizeBlogPage('0', 2)).toBe(1)
    expect(normalizeBlogPage('12', 2)).toBe(2)
    expect(normalizeBlogPage(['2', '1'], 2)).toBe(2)
  })
})
