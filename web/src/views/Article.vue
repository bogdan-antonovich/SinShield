<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import BaseButton from '@/common/components/BaseButton.vue'
import BaseContainer from '@/common/components/BaseContainer.vue'
import { usePageMetadata } from '@/common/composables/usePageMetadata'
import TheBreadCrumbs from '@/layouts/components/TheBreadCrumbs.vue'
import RichText from '@/modules/blog/components/RichText.vue'
import TableOfContents, {
  type TocEntry,
} from '@/modules/blog/components/TableOfContents.vue'
import {
  formatArticleDate,
  getArticles,
  getArticleBySlug,
  getArticleMetaTitle,
  getArticlePath,
} from '@/modules/blog/services/articles'
import type {
  ArticleBlock,
  ArticleSection,
  HeadingBlock,
  RichText as RichTextValue,
} from '@/modules/blog/types'

defineOptions({ name: 'ArticleView' })

const route = useRoute()
const article = computed(() => getArticleBySlug(String(route.params.slug)))
const relatedArticles = computed(() => {
  const current = article.value
  if (!current) return []

  return getArticles()
    .filter((candidate) => candidate.slug !== current.slug)
    .map((candidate) => ({
      article: candidate,
      sharedTags: candidate.tags.filter((tag) => current.tags.includes(tag)).length,
    }))
    .sort((left, right) => right.sharedTags - left.sharedTags || Date.parse(right.article.publishedAt) - Date.parse(left.article.publishedAt))
    .slice(0, 3)
    .map(({ article: candidate }) => candidate)
})

function resolveBlocks(section: ArticleSection): ArticleBlock[] {
  if (section.blocks) return section.blocks
  return (section.paragraphs ?? []).map((text): ArticleBlock => ({ type: 'paragraph', text }))
}

/** Collapse a RichText value (string or runs) down to plain text for slugs and labels. */
function richTextToPlain(value: RichTextValue): string {
  if (typeof value === 'string') return value
  return value.map((run) => (typeof run === 'string' ? run : run.text)).join('')
}

/**
 * Pre-render the sections with stable, unique heading ids so the anchors in the
 * article body line up with the table-of-contents links.
 */
const preparedSections = computed(() => {
  const used = new Map<string, number>()

  const slugify = (text: string): string => {
    const base =
      text
        .toLowerCase()
        .trim()
        .replace(/[^\w\s-]/g, '')
        .replace(/[\s_-]+/g, '-')
        .replace(/^-+|-+$/g, '') || 'section'

    const seen = used.get(base) ?? 0
    used.set(base, seen + 1)
    return seen === 0 ? base : `${base}-${seen}`
  }

  return (article.value?.sections ?? []).map((section, index) => {
    const headingId = section.heading ? slugify(section.heading) : undefined

    const blocks = resolveBlocks(section).map((block) => {
      if (block.type === 'heading') {
        return { ...block, id: slugify(richTextToPlain(block.text)) }
      }
      return block
    })

    return { key: section.heading ?? index, heading: section.heading, headingId, blocks }
  })
})

/** Nested TOC: section headings are top-level, in-section subheadings nest under them. */
const toc = computed<TocEntry[]>(() => {
  const entries: TocEntry[] = []

  for (const section of preparedSections.value) {
    const children: TocEntry[] = section.blocks
      .filter((block): block is HeadingBlock & { id: string } => block.type === 'heading')
      .map((block) => ({ id: block.id, label: richTextToPlain(block.text) }))

    if (section.heading && section.headingId) {
      entries.push({ id: section.headingId, label: section.heading, children })
    } else {
      // A section without its own heading still contributes any subheadings.
      entries.push(...children)
    }
  }

  return entries
})

const breadcrumbs = computed(() => [
  { label: 'Home', to: '/' },
  { label: 'Blog', to: '/blog' },
  { label: article.value?.title ?? 'Article' },
])

usePageMetadata(() => {
  const value = article.value

  if (!value) {
    return {
      title: 'Article not found — SinShield',
      description: 'The article you are looking for could not be found.',
      path: route.path,
    }
  }

  return {
    title: `${getArticleMetaTitle(value)} | SinShield`,
    description: value.description,
    path: getArticlePath(value),
    image: value.thumbnail,
    type: 'article',
    publishedAt: value.publishedAt,
    modifiedAt: value.updatedAt,
    author: value.author,
  }
})
</script>

<template>
  <main v-if="article" class="bg-background-soft pb-20 pt-28 sm:pb-28 sm:pt-32 lg:pb-32 lg:pt-40">
    <BaseContainer>
      <TheBreadCrumbs :items="breadcrumbs" color="#082D48" class="mb-10 sm:mb-14" />

      <header class="mx-auto max-w-5xl text-center">
        <h1 class="font-display text-[2.35rem] font-extrabold leading-[1.02] tracking-[-0.04em] text-navy sm:text-6xl lg:text-[5rem]">
          {{ article.title }}
        </h1>

        <p class="mx-auto mt-7 max-w-3xl text-lg leading-8 text-navy/65 sm:text-xl sm:leading-9">
          {{ article.description }}
        </p>

        <p class="mt-8 flex flex-wrap items-center justify-center gap-x-1.5 gap-y-1 text-sm font-semibold text-navy/55 sm:text-base">
          <RouterLink
            to="/authors/sinshield-editorial"
            class="rounded-sm underline-offset-4 hover:text-primary hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/35"
          >{{ article.author }}</RouterLink><span>,</span>
          <time :datetime="article.publishedAt">{{ formatArticleDate(article.publishedAt) }},</time>
          <span v-if="article.updatedAt">updated <time :datetime="article.updatedAt">{{ formatArticleDate(article.updatedAt) }}</time>,</span>
          <span>{{ article.readingTime }}</span>
        </p>

        <ul class="mt-7 flex flex-wrap justify-center gap-2" aria-label="Article topics">
          <li
            v-for="tag in article.tags"
            :key="tag"
            class="rounded-full bg-primary/8 px-4 py-2 text-sm font-semibold text-primary"
          >
            {{ tag }}
          </li>
        </ul>
      </header>

      <div class="mt-9 flex justify-center sm:mt-12">
        <BaseButton to="/products/android" class="w-full sm:w-auto">Block Adult Content with SinShield</BaseButton>
      </div>

      <figure class="mx-auto mt-8 max-w-6xl overflow-hidden rounded-[2rem] bg-background-mute shadow-2xl shadow-primary/10 sm:mt-10">
        <img
          :src="article.thumbnail"
          :alt="article.thumbnailAlt"
          class="aspect-[16/9] w-full object-cover"
        />
      </figure>

      <div class="mt-16 sm:mt-20 lg:grid lg:grid-cols-[14rem_minmax(0,1fr)] lg:items-start lg:gap-x-32">
        <TableOfContents
          :entries="toc"
          class="hidden lg:block lg:sticky lg:top-28"
        />

        <article class="article-body">
          <section v-for="section in preparedSections" :key="section.key">
            <h2 v-if="section.heading" :id="section.headingId">{{ section.heading }}</h2>

            <template v-for="(block, blockIndex) in section.blocks" :key="blockIndex">
              <p v-if="block.type === 'paragraph'">
                <RichText :value="block.text" />
              </p>

              <h3 v-else-if="block.type === 'heading'" :id="block.id">
                <RichText :value="block.text" />
              </h3>

            <ul v-else-if="block.type === 'list' && !block.ordered" class="article-list">
              <li v-for="(item, itemIndex) in block.items" :key="itemIndex">
                <RichText :value="item" />
              </li>
            </ul>

            <ol v-else-if="block.type === 'list'" class="article-list article-list-ordered">
              <li v-for="(item, itemIndex) in block.items" :key="itemIndex">
                <RichText :value="item" />
              </li>
            </ol>

            <div v-else-if="block.type === 'table'" class="article-table-wrap">
              <table class="article-table">
                <thead>
                  <tr>
                    <th v-for="(column, columnIndex) in block.columns" :key="columnIndex" scope="col">
                      {{ column }}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, rowIndex) in block.rows" :key="rowIndex">
                    <td v-for="(cell, cellIndex) in row" :key="cellIndex">
                      <RichText :value="cell" />
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <aside v-else-if="block.type === 'callout'" class="article-callout">
              <p v-if="block.label" class="article-callout-label">{{ block.label }}</p>
              <p class="article-callout-text">
                <RichText :value="block.text" />
              </p>
            </aside>
            </template>
          </section>
        </article>
      </div>

      <div class="mx-auto mt-16 max-w-3xl border-t border-navy/10 pt-8">
        <div class="mb-12 rounded-3xl bg-white p-7 ring-1 ring-navy/8 sm:p-8">
          <p class="font-display text-sm font-bold uppercase tracking-[0.16em] text-primary">About this article</p>
          <p class="mt-3 leading-7 text-navy/70">Published by <RouterLink to="/authors/sinshield-editorial" class="font-semibold text-primary underline underline-offset-4">SinShield Editorial</RouterLink> under our <RouterLink to="/editorial-standards" class="font-semibold text-primary underline underline-offset-4">editorial standards</RouterLink>. Product ownership, limitations, source quality, and corrections are reviewed before publication.</p>
        </div>

        <RouterLink
          to="/blog"
          class="inline-flex items-center gap-2 rounded-md font-display font-bold text-primary underline-offset-4 hover:underline focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-primary/35"
        >
          <span aria-hidden="true">←</span>
          Back to the library
        </RouterLink>
      </div>

      <section v-if="relatedArticles.length" class="mx-auto mt-16 max-w-5xl border-t border-navy/10 pt-12" aria-labelledby="related-reading-heading">
        <h2 id="related-reading-heading" class="font-display text-3xl font-extrabold tracking-[-0.03em] text-navy">Related reading</h2>
        <div class="mt-7 grid gap-5 sm:grid-cols-3">
          <RouterLink
            v-for="related in relatedArticles"
            :key="related.slug"
            :to="getArticlePath(related)"
            class="rounded-2xl bg-white p-6 font-display text-lg font-bold leading-snug text-navy ring-1 ring-navy/8 transition hover:-translate-y-1 hover:text-primary hover:shadow-lg focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-primary/35 motion-reduce:transition-none"
          >
            {{ related.title }}
          </RouterLink>
        </div>
      </section>
    </BaseContainer>
  </main>

  <main v-else class="grid min-h-[70vh] place-items-center bg-background-soft px-6 pb-24 pt-40 text-center">
    <div>
      <p class="text-sm font-bold uppercase tracking-[0.18em] text-primary">404</p>
      <h1 class="mt-4 font-display text-5xl font-extrabold tracking-[-0.04em] text-navy">Article not found</h1>
      <p class="mt-5 text-lg text-navy/60">The story may have moved or the address may be incorrect.</p>
      <RouterLink
        to="/blog"
        class="mt-8 inline-flex rounded-full bg-primary px-7 py-4 font-display font-bold text-white"
      >
        Visit the blog
      </RouterLink>
    </div>
  </main>
</template>

<style scoped>
.article-body section + section {
  margin-top: 3rem;
}

/* Keep anchored headings clear of the fixed site header when jumped to. */
.article-body h2,
.article-body h3 {
  scroll-margin-top: 7rem;
}

.article-body h2 {
  margin-bottom: 1.25rem;
  color: var(--ss-navy);
  font-family: var(--font-display);
  font-size: clamp(1.75rem, 4vw, 2.25rem);
  font-weight: 750;
  letter-spacing: -0.03em;
  line-height: 1.1;
}

.article-body h3 {
  color: var(--ss-navy);
  font-family: var(--font-display);
  font-size: clamp(1.35rem, 3vw, 1.6rem);
  font-weight: 700;
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.article-body p {
  color: rgb(8 45 72 / 76%);
  font-size: 1.125rem;
  line-height: 1.85;
}

/* Vertical rhythm between blocks. The heading already spaces the block that
   follows it, so only later siblings pick up a top margin. */
.article-body section > p,
.article-body section > ul,
.article-body section > ol,
.article-body section > .article-table-wrap,
.article-body section > .article-callout {
  margin-top: 1.6rem;
}

.article-body section > h3 {
  margin-top: 2.6rem;
  margin-bottom: 0.4rem;
}

.article-body section > :first-child,
.article-body section > h2 + * {
  margin-top: 0;
}

.article-body .article-list {
  padding-left: 1.3rem;
  color: rgb(8 45 72 / 76%);
  font-size: 1.125rem;
  line-height: 1.7;
  list-style-position: outside;
}

.article-body ul.article-list {
  list-style-type: disc;
}

.article-body ol.article-list {
  list-style-type: decimal;
}

.article-body .article-list li + li {
  margin-top: 0.55rem;
}

.article-body .article-list li::marker {
  color: var(--ss-primary);
}

.article-body .article-table-wrap {
  overflow-x: auto;
  border: 1px solid rgb(8 45 72 / 10%);
  border-radius: 1rem;
}

.article-body .article-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.95rem;
  line-height: 1.5;
  text-align: left;
}

.article-body .article-table th {
  background: var(--color-background-mute);
  color: var(--ss-navy);
  font-family: var(--font-display);
  font-size: 0.82rem;
  font-weight: 700;
  letter-spacing: 0.01em;
  padding: 0.85rem 1rem;
  white-space: nowrap;
}

.article-body .article-table td {
  padding: 0.85rem 1rem;
  color: rgb(8 45 72 / 78%);
  vertical-align: top;
  border-top: 1px solid rgb(8 45 72 / 8%);
}

.article-body .article-table td:first-child {
  color: var(--ss-navy);
  font-weight: 600;
}

.article-body .article-callout {
  border-left: 3px solid var(--ss-primary);
  border-radius: 0.75rem;
  background: rgb(8 124 240 / 6%);
  padding: 1.1rem 1.3rem;
}

.article-body .article-callout-label {
  margin-bottom: 0.3rem;
  color: var(--ss-primary);
  font-family: var(--font-display);
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.article-body .article-callout-text {
  color: var(--ss-navy);
  font-size: 1.05rem;
  line-height: 1.7;
}
</style>
