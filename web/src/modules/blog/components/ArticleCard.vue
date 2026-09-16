<script setup lang="ts">
import { RouterLink } from 'vue-router'

import { formatArticleDate, getArticlePath } from '@/modules/blog/services/articles'
import type { Article } from '@/modules/blog/types'

defineProps<{
  article: Article
}>()
</script>

<template>
  <article class="group min-w-0">
    <RouterLink
      :to="getArticlePath(article)"
      class="block overflow-hidden rounded-[1.5rem] bg-background-mute focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-primary/35"
      :aria-label="`Read ${article.title}`"
    >
      <img
        :src="article.thumbnail"
        :alt="article.thumbnailAlt"
        class="aspect-[16/10] w-full object-cover transition duration-500 ease-out group-hover:scale-[1.025] motion-reduce:transition-none"
        loading="lazy"
      />
    </RouterLink>

    <div class="pt-6">
      <h3 class="font-display text-2xl font-bold leading-[1.12] tracking-[-0.025em] text-navy sm:text-[1.65rem]">
        <RouterLink
          :to="getArticlePath(article)"
          class="rounded-md decoration-primary/30 decoration-2 underline-offset-4 transition group-hover:text-primary group-hover:underline focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-primary/35"
        >
          {{ article.title }}
        </RouterLink>
      </h3>

      <p class="mt-4 flex flex-wrap items-center gap-x-1.5 gap-y-1 text-sm font-medium text-navy/55">
        <span>{{ article.author }},</span>
        <time :datetime="article.publishedAt">{{ formatArticleDate(article.publishedAt) }}</time>
      </p>
    </div>
  </article>
</template>
