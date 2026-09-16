<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

import type { RichText } from '@/modules/blog/types'

const props = defineProps<{
  value: RichText
}>()

const runs = computed(() => (typeof props.value === 'string' ? [props.value] : props.value))
</script>

<template>
  <template v-for="(run, index) in runs" :key="index">
    <template v-if="typeof run === 'string'">{{ run }}</template>
    <RouterLink
      v-else-if="run.href && run.href.startsWith('/')"
      :to="run.href"
      class="article-link"
    >{{ run.text }}</RouterLink>
    <a
      v-else-if="run.href"
      :href="run.href"
      target="_blank"
      rel="noopener noreferrer"
      class="article-link"
    >{{ run.text }}</a>
    <strong v-else-if="run.bold" class="article-strong">{{ run.text }}</strong>
    <template v-else>{{ run.text }}</template>
  </template>
</template>

<style scoped>
.article-link {
  color: var(--ss-primary);
  font-weight: 600;
  text-decoration: underline;
  text-decoration-color: rgb(8 124 240 / 35%);
  text-underline-offset: 3px;
  transition: text-decoration-color 150ms ease;
}

.article-link:hover {
  text-decoration-color: var(--ss-primary);
}

.article-strong {
  color: var(--ss-navy);
  font-weight: 700;
}
</style>
