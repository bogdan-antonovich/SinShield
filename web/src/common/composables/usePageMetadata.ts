import { watchEffect, type MaybeRefOrGetter } from 'vue'
import { toValue } from 'vue'

interface PageMetadata {
  title: string
  description: string
  path: string
  image?: string
  type?: 'website' | 'article'
  publishedAt?: string
  author?: string
}

const productionOrigin = (import.meta.env.VITE_SITE_URL || 'https://sinshield.app').replace(/\/$/, '')

function upsertMeta(attribute: 'name' | 'property', key: string, content: string) {
  let element = document.head.querySelector<HTMLMetaElement>(`meta[${attribute}="${key}"]`)

  if (!element) {
    element = document.createElement('meta')
    element.setAttribute(attribute, key)
    document.head.append(element)
  }

  element.content = content
}

function removeMeta(attribute: 'name' | 'property', key: string) {
  document.head.querySelector(`meta[${attribute}="${key}"]`)?.remove()
}

function toAbsoluteUrl(value: string): string {
  return value.startsWith('http') ? value : `${productionOrigin}${value.startsWith('/') ? '' : '/'}${value}`
}

export function usePageMetadata(metadata: MaybeRefOrGetter<PageMetadata>) {
  watchEffect(() => {
    const value = toValue(metadata)
    const canonicalUrl = `${productionOrigin}${value.path}`

    document.title = value.title
    upsertMeta('name', 'description', value.description)
    upsertMeta('property', 'og:title', value.title)
    upsertMeta('property', 'og:description', value.description)
    upsertMeta('property', 'og:type', value.type ?? 'website')
    upsertMeta('property', 'og:url', canonicalUrl)
    upsertMeta('name', 'twitter:card', value.image ? 'summary_large_image' : 'summary')
    upsertMeta('name', 'twitter:title', value.title)
    upsertMeta('name', 'twitter:description', value.description)

    if (value.image) {
      const absoluteImage = toAbsoluteUrl(value.image)
      upsertMeta('property', 'og:image', absoluteImage)
      upsertMeta('name', 'twitter:image', absoluteImage)
    } else {
      removeMeta('property', 'og:image')
      removeMeta('name', 'twitter:image')
    }

    if (value.publishedAt) {
      upsertMeta('property', 'article:published_time', value.publishedAt)
    } else {
      removeMeta('property', 'article:published_time')
    }

    if (value.author) {
      upsertMeta('property', 'article:author', value.author)
    } else {
      removeMeta('property', 'article:author')
    }

    let canonical = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]')
    if (!canonical) {
      canonical = document.createElement('link')
      canonical.rel = 'canonical'
      document.head.append(canonical)
    }
    canonical.href = canonicalUrl
  })
}
