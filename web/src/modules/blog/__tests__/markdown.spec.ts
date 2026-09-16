import { describe, expect, it } from 'vitest'

import { parseArticleCollection, parseArticleMarkdown } from '@/modules/blog/markdown.mjs'

const frontMatter = `---
slug: example
title: "Example"
description: "Description"
author: "SinShield Editorial"
publishedAt: 2026-09-15
readingTime: "3 min read"
thumbnail: "/images/example.webp"
thumbnailAlt: "Example image"
tags: ["Focus"]
---`

describe('article Markdown', () => {
  it('parses the supported editorial blocks', () => {
    const article = parseArticleMarkdown(`${frontMatter}

An **important** paragraph with a [source](https://example.com).

> [!NOTE] Remember
> A useful takeaway.

## Main section

### Steps

1. First
2. Second

| Option | Use |
| --- | --- |
| One | Test |
`)

    expect(article.slug).toBe('example')
    expect(article.sections).toHaveLength(2)
    expect(article.sections[0]?.blocks?.map((block) => block.type)).toEqual([
      'paragraph',
      'callout',
    ])
    expect(article.sections[0]?.blocks?.[0]).toEqual({
      type: 'paragraph',
      text: [
        'An ',
        { text: 'important', bold: true },
        ' paragraph with a ',
        { text: 'source', href: 'https://example.com' },
        '.',
      ],
    })
    expect(article.sections[1]?.blocks?.map((block) => block.type)).toEqual([
      'heading',
      'list',
      'table',
    ])
  })

  it('puts a featured article first and otherwise sorts newest first', () => {
    const olderFeatured = `${frontMatter
      .replace('slug: example', 'slug: older')
      .replace('2026-09-15', '2026-01-01')
      .replace('\n---', '\nfeatured: true\n---')}

Featured.`
    const latest = `${frontMatter.replace('slug: example', 'slug: latest')}

Latest.`

    expect(parseArticleCollection({ olderFeatured, latest }).map((article) => article.slug)).toEqual([
      'older',
      'latest',
    ])
  })
})
