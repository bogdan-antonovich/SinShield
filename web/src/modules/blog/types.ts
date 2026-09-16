export interface TextRun {
  text: string
  href?: string
  bold?: boolean
}

/**
 * Inline article content. A plain string for simple prose, or an ordered list of
 * runs so a paragraph can mix text with citation links and light emphasis.
 */
export type RichText = string | Array<string | TextRun>

export interface ParagraphBlock {
  type: 'paragraph'
  text: RichText
}

export interface HeadingBlock {
  type: 'heading'
  text: RichText
}

export interface ListBlock {
  type: 'list'
  ordered?: boolean
  items: RichText[]
}

export interface TableBlock {
  type: 'table'
  columns: string[]
  rows: RichText[][]
}

export interface CalloutBlock {
  type: 'callout'
  label?: string
  text: RichText
}

export type ArticleBlock =
  | ParagraphBlock
  | HeadingBlock
  | ListBlock
  | TableBlock
  | CalloutBlock

export interface ArticleSection {
  heading?: string
  /** Legacy simple prose. Rendered as a sequence of paragraphs when `blocks` is absent. */
  paragraphs?: string[]
  /** Rich content: paragraphs, subheadings, lists, tables, and callouts. */
  blocks?: ArticleBlock[]
}

export interface Article {
  slug: string
  title: string
  description: string
  author: string
  publishedAt: string
  readingTime: string
  thumbnail: string
  thumbnailAlt: string
  tags: string[]
  /** Pins this article above the normal newest-first order. Use on at most one article. */
  featured?: boolean
  sections: ArticleSection[]
}
