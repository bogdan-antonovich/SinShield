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

      <header class="mb-12 max-w-4xl sm:mb-16">
        <p class="font-display text-sm font-bold uppercase tracking-[0.18em] text-primary">SinShield guides</p>
        <h1 class="mt-4 font-display text-[2.35rem] font-extrabold leading-[1.02] tracking-[-0.04em] text-navy sm:text-6xl lg:text-[4.6rem]">
          Practical guidance for healthier digital habits
        </h1>
        <p class="mt-6 max-w-3xl text-lg leading-8 text-navy/65 sm:text-xl">
          Evidence-aware, judgment-free articles about adult-content blocking, focus, and making a clear decision easier to keep when a difficult moment arrives.
        </p>
      </header>

      <article class="grid items-center gap-9 border-t border-navy/10 pt-10 lg:grid-cols-[minmax(0,1.05fr)_minmax(360px,0.95fr)] lg:gap-16 lg:pt-14 xl:gap-24">
        <div class="order-2 lg:order-1">
          <p class="mb-6 flex flex-wrap items-center gap-x-1.5 gap-y-1 text-sm font-semibold text-navy/60 sm:text-base">
            <span>{{ article.author }},</span>
            <time :datetime="article.publishedAt">{{ formatArticleDate(article.publishedAt) }}</time>
          </p>

          <p class="mb-4 font-display text-xs font-bold uppercase tracking-[0.18em] text-primary">Featured article</p>
          <h2 class="max-w-3xl font-display text-[2.35rem] font-extrabold leading-[1.02] tracking-[-0.04em] text-navy sm:text-[3.35rem] lg:text-[4.4rem]">
            <RouterLink
              :to="getArticlePath(article)"
              class="rounded-lg decoration-primary/35 decoration-4 underline-offset-[10px] transition hover:text-primary hover:underline focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-primary/35"
            >
              {{ article.title }}
            </RouterLink>
          </h2>

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
