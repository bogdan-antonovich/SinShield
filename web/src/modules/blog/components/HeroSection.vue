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
  <section id="hero" class="overflow-hidden">
    <div class="app-shell relative z-40 min-h-[72svh]">
      <BaseContainer
        class="flex min-h-[72svh] flex-col items-center justify-center pb-20 pt-28 sm:pb-24 sm:pt-32 md:pb-28 md:pt-36 lg:pb-32 lg:pt-40"
      >
        <TheBreadCrumbs :items="breadcrumbs" color="#ffffff" class="mb-10 self-start sm:mb-14" />

        <header class="flex flex-col items-center">
          <h1 class="mb-6 max-w-248.75 text-center text-[clamp(2.1rem,9.5vw,3rem)] font-extrabold leading-[1.08] tracking-[-0.025em] text-white sm:text-[44px] md:text-[56px] lg:mb-8 lg:text-[4rem]">
            The SinShield Blog
          </h1>
          <p class="max-w-236.75 text-center text-[17px] font-normal leading-[1.5] text-white/85 sm:text-xl md:text-2xl">
            If you are trying to keep porn out of your life, you probably have questions about what works and what does not. We answer them honestly, so you can make a plan that fits your life and holds up when the moment gets difficult.
          </p>
        </header>
      </BaseContainer>
    </div>

    <div class="bg-surface py-16 sm:py-20 lg:py-32">
      <BaseContainer>
        <article class="grid items-center gap-9 lg:grid-cols-[minmax(0,1.05fr)_minmax(360px,0.95fr)] lg:gap-16 xl:gap-24">
          <div class="order-2 lg:order-1">
            <p class="mb-6 flex flex-wrap items-center gap-x-1.5 gap-y-1 text-sm font-semibold text-navy/60 sm:text-base">
              <span>{{ article.author }},</span>
              <time :datetime="article.publishedAt">{{ formatArticleDate(article.publishedAt) }}</time>
            </p>

            <h2 class="max-w-3xl font-display text-[2.35rem] font-extrabold leading-[1.02] tracking-[-0.04em] text-navy sm:text-[3.35rem] lg:text-[4.4rem] xl:text-[5.1rem]">
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
    </div>
  </section>
</template>
