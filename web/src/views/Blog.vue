<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import { usePageMetadata } from '@/common/composables/usePageMetadata'
import HeroSection from '@/modules/blog/components/HeroSection.vue'
import LibrarySection from '@/modules/blog/components/LibrarySection.vue'
import {
  getArticles,
  getBlogPage,
  getBlogPageCount,
  normalizeBlogPage,
} from '@/modules/blog/services/articles'

defineOptions({ name: 'BlogView' })

const articles = getArticles()
const featuredArticle = articles[0]
const libraryArticles = articles.slice(1)
const route = useRoute()
const totalPages = getBlogPageCount(libraryArticles.length)
const currentPage = computed(() => normalizeBlogPage(route.query.page, totalPages))
const visibleArticles = computed(() => getBlogPage(libraryArticles, currentPage.value))

usePageMetadata(() => ({
  title:
    currentPage.value === 1
      ? 'SinShield Blog — Healthier Digital Habits'
      : `SinShield Blog — Page ${currentPage.value}`,
  description:
    'Practical, private, and judgment-free guidance for healthier digital habits, focus, and recovery.',
  path: currentPage.value === 1 ? '/blog' : `/blog?page=${currentPage.value}`,
  image: featuredArticle?.thumbnail,
}))
</script>

<template>
  <main>
    <HeroSection v-if="featuredArticle" :article="featuredArticle" />
    <LibrarySection
      :articles="visibleArticles"
      :current-page="currentPage"
      :total-pages="totalPages"
    />
  </main>
</template>
