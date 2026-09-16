<script setup lang="ts">
import BaseContainer from '@/common/components/BaseContainer.vue'
import ArticleCard from '@/modules/blog/components/ArticleCard.vue'
import BlogPagination from '@/modules/blog/components/BlogPagination.vue'
import type { Article } from '@/modules/blog/types'

defineProps<{
  articles: Article[]
  currentPage: number
  totalPages: number
}>()
</script>

<template>
  <section
    id="library"
    class="scroll-mt-8 bg-background-soft py-16 sm:py-20 lg:py-32"
    aria-labelledby="library-heading"
  >
    <BaseContainer>
      <div class="mb-12 border-b border-navy/10 pb-6 sm:mb-16">
        <h2
          id="library-heading"
          class="font-display text-[2.35rem] font-extrabold leading-none tracking-[-0.04em] text-navy sm:text-5xl lg:text-6xl"
        >
          The library
        </h2>
      </div>

      <div
        v-if="articles.length"
        class="grid gap-x-8 gap-y-14 sm:grid-cols-2 lg:grid-cols-3 lg:gap-y-20"
      >
        <ArticleCard v-for="article in articles" :key="article.slug" :article="article" />
      </div>

      <p v-else class="py-12 text-lg text-navy/60">More articles are on the way.</p>

      <BlogPagination :current-page="currentPage" :total-pages="totalPages" />
    </BaseContainer>
  </section>
</template>
