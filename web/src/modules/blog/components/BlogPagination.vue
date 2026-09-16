<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

const props = defineProps<{
  currentPage: number
  totalPages: number
}>()

const pages = computed(() => Array.from({ length: props.totalPages }, (_, index) => index + 1))

function pageLocation(page: number) {
  return {
    name: 'blog',
    query: page === 1 ? {} : { page: String(page) },
    hash: '#library',
  }
}
</script>

<template>
  <nav
    v-if="totalPages > 1"
    class="mt-16 flex flex-wrap items-center justify-center gap-2 border-t border-navy/10 pt-10 sm:mt-20"
    aria-label="Blog pagination"
  >
    <RouterLink
      v-if="currentPage > 1"
      :to="pageLocation(currentPage - 1)"
      class="mr-2 inline-flex min-h-11 items-center rounded-full px-4 text-sm font-bold text-navy transition hover:bg-white hover:text-primary focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-primary/35"
      rel="prev"
    >
      <span aria-hidden="true">←</span>
      <span class="ml-2">Previous</span>
    </RouterLink>
    <span
      v-else
      class="mr-2 inline-flex min-h-11 items-center rounded-full px-4 text-sm font-bold text-navy/30"
      aria-disabled="true"
    >
      <span aria-hidden="true">←</span>
      <span class="ml-2">Previous</span>
    </span>

    <RouterLink
      v-for="page in pages"
      :key="page"
      :to="pageLocation(page)"
      class="inline-flex size-11 items-center justify-center rounded-full text-sm font-bold transition focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-primary/35"
      :class="
        page === currentPage
          ? 'bg-navy text-white'
          : 'bg-white text-navy shadow-sm hover:bg-primary hover:text-white'
      "
      :aria-label="`Page ${page}`"
      :aria-current="page === currentPage ? 'page' : undefined"
    >
      {{ page }}
    </RouterLink>

    <RouterLink
      v-if="currentPage < totalPages"
      :to="pageLocation(currentPage + 1)"
      class="ml-2 inline-flex min-h-11 items-center rounded-full px-4 text-sm font-bold text-navy transition hover:bg-white hover:text-primary focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-primary/35"
      rel="next"
    >
      <span class="mr-2">Next</span>
      <span aria-hidden="true">→</span>
    </RouterLink>
    <span
      v-else
      class="ml-2 inline-flex min-h-11 items-center rounded-full px-4 text-sm font-bold text-navy/30"
      aria-disabled="true"
    >
      <span class="mr-2">Next</span>
      <span aria-hidden="true">→</span>
    </span>
  </nav>
</template>
