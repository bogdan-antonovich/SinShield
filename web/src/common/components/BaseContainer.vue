<script setup lang="ts">
withDefaults(
  defineProps<{
    as?: string
  }>(),
  {
    as: 'div',
  },
)
</script>

<template>
  <component :is="as" class="mx-auto w-full max-w-7xl px-5 sm:px-7 lg:px-8">
    <slot />
  </component>
</template>
