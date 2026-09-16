<script setup lang="ts">
import { RouterLink, type RouteLocationRaw } from 'vue-router'

withDefaults(defineProps<{
  items: Array<{
    label: string
    to?: RouteLocationRaw
  }>
  color?: string
}>(), {
  color: '#ffffff',
})
</script>

<template>
  <nav :style="{ color }" aria-label="Breadcrumb">
    <ol class="flex flex-wrap items-center gap-2 font-display text-sm font-semibold sm:text-base">
      <li v-for="(item, index) in items" :key="item.label" class="flex items-center gap-2">
        <svg
          v-if="index > 0"
          class="size-4 opacity-45"
          viewBox="0 0 20 20"
          fill="none"
          aria-hidden="true"
        >
          <path
            d="m7.5 4.5 5 5.5-5 5.5"
            stroke="currentColor"
            stroke-width="1.8"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>

        <RouterLink
          v-if="item.to"
          :to="item.to"
          class="opacity-70 underline-offset-4 transition hover:opacity-100 hover:underline focus-visible:rounded-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-current/50"
        >
          {{ item.label }}
        </RouterLink>
        <span v-else aria-current="page">{{ item.label }}</span>
      </li>
    </ol>
  </nav>
</template>
