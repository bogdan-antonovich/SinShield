<script setup lang="ts">
import { RouterLink } from 'vue-router'

import BaseButton from '@/common/components/BaseButton.vue'
import BaseContainer from '@/common/components/BaseContainer.vue'
import TheBreadCrumbs from '@/layouts/components/TheBreadCrumbs.vue'
import { formatArticleDate, getArticlePath } from '@/modules/blog/services/articles'
import type { Article } from '@/modules/blog/types'

defineProps<{
  article: Article
}>()

const breadcrumbs = [
  { label: 'Home', to: '/' },
  { label: 'Blog' },
]
</script>

<template>
  <section id="hero" class="overflow-hidden bg-surface pb-16 pt-28 sm:pb-20 sm:pt-32 lg:pb-32 lg:pt-40">
    <BaseContainer>
      <TheBreadCrumbs
        :items="breadcrumbs"
        color="#000000"
        class="mb-10 self-start text-navy sm:mb-14"
      />

      <article class="grid items-center gap-9 lg:grid-cols-[minmax(0,1.05fr)_minmax(360px,0.95fr)] lg:gap-16 xl:gap-24">
        <div class="order-2 lg:order-1">
          <p class="mb-6 flex flex-wrap items-center gap-x-1.5 gap-y-1 text-sm font-semibold text-navy/60 sm:text-base">
            <span>{{ article.author }},</span>
            <time :datetime="article.publishedAt">{{ formatArticleDate(article.publishedAt) }}</time>
          </p>

          <h1 class="max-w-3xl font-display text-[2.35rem] font-extrabold leading-[1.02] tracking-[-0.04em] text-navy sm:text-[3.35rem] lg:text-[4.4rem] xl:text-[5.1rem]">
            <RouterLink
              :to="getArticlePath(article)"
              class="rounded-lg decoration-primary/35 decoration-4 underline-offset-[10px] transition hover:text-primary hover:underline focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-primary/35"
            >
              {{ article.title }}
            </RouterLink>
          </h1>

          <ul class="mt-8 flex flex-wrap gap-2" aria-label="Article topics">
            <li
              v-for="tag in article.tags"
              :key="tag"
              class="rounded-full bg-primary/8 px-4 py-2 text-sm font-semibold text-primary"
            >
              {{ tag }}
            </li>
          </ul>

          <BaseButton :to="getArticlePath(article)" class="mt-10 sm:mt-12">
            Read more
          </BaseButton>
        </div>

        <RouterLink
          :to="getArticlePath(article)"
          class="group order-1 block overflow-hidden rounded-[2rem] bg-background-mute shadow-2xl shadow-primary/10 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-primary/35 lg:order-2"
          :aria-label="`Read ${article.title}`"
        >
          <img
            :src="article.thumbnail"
            :alt="article.thumbnailAlt"
            class="aspect-square size-full object-cover transition duration-500 ease-out group-hover:scale-[1.025] motion-reduce:transition-none"
          />
        </RouterLink>
      </article>
    </BaseContainer>
  </section>
</template>
