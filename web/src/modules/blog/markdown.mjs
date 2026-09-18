import matter from 'gray-matter'
import remarkGfm from 'remark-gfm'
import remarkParse from 'remark-parse'
import { unified } from 'unified'

const markdownParser = unified().use(remarkParse).use(remarkGfm)
const requiredStringMetadata = [
  'slug',
  'title',
  'description',
  'author',
  'publishedAt',
  'readingTime',
  'thumbnail',
  'thumbnailAlt',
]

function fail(sourceName, message) {
  throw new Error(`${sourceName}: ${message}`)
}

function plainText(node) {
  if (typeof node.value === 'string') return node.value
  return (node.children ?? []).map(plainText).join('')
}

function phrasingContent(nodes, sourceName) {
  const runs = []

  function append(run) {
    if (typeof run === 'string' ? !run : !run.text) return
    const previous = runs.at(-1)
    if (typeof run === 'string' && typeof previous === 'string') {
      runs[runs.length - 1] += run
    } else if (
      typeof run !== 'string' &&
      typeof previous !== 'string' &&
      previous?.href === run.href &&
      previous?.bold === run.bold
    ) {
      previous.text += run.text
    } else {
      runs.push(run)
    }
  }

  function visit(node, attributes = {}) {
    switch (node.type) {
      case 'text':
        append(Object.keys(attributes).length ? { text: node.value, ...attributes } : node.value)
        break
      case 'strong':
        node.children.forEach((child) => visit(child, { ...attributes, bold: true }))
        break
      case 'link':
        node.children.forEach((child) => visit(child, { ...attributes, href: node.url }))
        break
      case 'emphasis':
      case 'delete':
        node.children.forEach((child) => visit(child, attributes))
        break
      case 'inlineCode':
        append(Object.keys(attributes).length ? { text: node.value, ...attributes } : node.value)
        break
      case 'break':
        append(' ')
        break
      default:
        fail(sourceName, `unsupported inline Markdown: ${node.type}`)
    }
  }

  nodes.forEach((node) => visit(node))
  return runs.length === 1 && typeof runs[0] === 'string' ? runs[0] : runs
}

function paragraphBlock(node, sourceName) {
  return { type: 'paragraph', text: phrasingContent(node.children, sourceName) }
}

function listBlock(node, sourceName) {
  const items = node.children.map((item) => {
    if (item.children.length !== 1 || item.children[0].type !== 'paragraph') {
      fail(sourceName, 'nested lists and multi-paragraph list items are not supported by the article layout')
    }
    return phrasingContent(item.children[0].children, sourceName)
  })
  return { type: 'list', ...(node.ordered ? { ordered: true } : {}), items }
}

function tableBlock(node, sourceName) {
  const [header, ...bodyRows] = node.children
  if (!header) fail(sourceName, 'table must contain a header row')
  const cellContent = (cell) => phrasingContent(cell.children, sourceName)
  return {
    type: 'table',
    columns: header.children.map((cell) => plainText(cell)),
    rows: bodyRows.map((row) => row.children.map(cellContent)),
  }
}

function calloutBlock(node, sourceName) {
  if (node.children.length !== 1 || node.children[0].type !== 'paragraph') {
    fail(sourceName, 'a callout must be one blockquote paragraph beginning with [!NOTE]')
  }
  const content = plainText(node.children[0])
  const match = content.match(/^\[!NOTE\][ \t]*([^\n]*)\n?([\s\S]*)$/)
  if (!match) fail(sourceName, 'blockquotes must use the callout syntax: > [!NOTE] Label')
  return {
    type: 'callout',
    ...(match[1] ? { label: match[1].trim() } : {}),
    text: match[2].trim(),
  }
}

function parseBody(body, sourceName) {
  const tree = markdownParser.parse(body)
  const sections = [{ blocks: [] }]
  let section = sections[0]

  for (const node of tree.children) {
    if (node.type === 'heading' && node.depth === 2) {
      section = { heading: plainText(node), blocks: [] }
      sections.push(section)
      continue
    }
    if (node.type === 'heading' && node.depth === 3) {
      section.blocks.push({ type: 'heading', text: phrasingContent(node.children, sourceName) })
      continue
    }

    switch (node.type) {
      case 'paragraph':
        section.blocks.push(paragraphBlock(node, sourceName))
        break
      case 'list':
        section.blocks.push(listBlock(node, sourceName))
        break
      case 'table':
        section.blocks.push(tableBlock(node, sourceName))
        break
      case 'blockquote':
        section.blocks.push(calloutBlock(node, sourceName))
        break
      default:
        fail(sourceName, `unsupported article-level Markdown: ${node.type}`)
    }
  }

  const populatedSections = sections.filter((candidate) => candidate.heading || candidate.blocks.length)
  if (!populatedSections.length) fail(sourceName, 'article body is empty')
  return populatedSections
}

function normalizedMetadata(data) {
  const metadata = { ...data }
  for (const field of ['publishedAt', 'updatedAt']) {
    if (metadata[field] instanceof Date) {
      metadata[field] = metadata[field].toISOString().slice(0, 10)
    }
  }
  return metadata
}

export function parseArticleMarkdown(source, sourceName = 'article') {
  const parsed = matter(source)
  const metadata = normalizedMetadata(parsed.data)

  for (const key of requiredStringMetadata) {
    if (typeof metadata[key] !== 'string' || !metadata[key].trim()) {
      fail(sourceName, `missing or invalid front matter field "${key}"`)
    }
  }
  if (!Array.isArray(metadata.tags) || metadata.tags.some((tag) => typeof tag !== 'string')) {
    fail(sourceName, '"tags" must be an array of strings')
  }
  if (metadata.featured !== undefined && typeof metadata.featured !== 'boolean') {
    fail(sourceName, '"featured" must be true or false')
  }
  if (metadata.updatedAt !== undefined && typeof metadata.updatedAt !== 'string') {
    fail(sourceName, '"updatedAt" must be an ISO date string')
  }

  return { ...metadata, sections: parseBody(parsed.content, sourceName) }
}

export function parseArticleCollection(sources) {
  const articles = Object.entries(sources).map(([sourceName, source]) =>
    parseArticleMarkdown(source, sourceName),
  )
  const slugs = new Set()
  for (const article of articles) {
    if (slugs.has(article.slug)) throw new Error(`Duplicate article slug: ${article.slug}`)
    slugs.add(article.slug)
  }
  const featuredArticles = articles.filter((article) => article.featured)
  if (featuredArticles.length > 1) {
    throw new Error(`Only one article can be featured: ${featuredArticles.map(({ slug }) => slug).join(', ')}`)
  }
  return articles.sort((left, right) => {
    if (Boolean(left.featured) !== Boolean(right.featured)) return left.featured ? -1 : 1
    return Date.parse(right.publishedAt) - Date.parse(left.publishedAt)
  })
}
