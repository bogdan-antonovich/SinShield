<script setup lang="ts">
import { usePageMetadata } from '@/common/composables/usePageMetadata'
import FeaturesSection from '@/modules/android/components/FeaturesSection.vue';
import HeroSection from '@/modules/android/components/HeroSection.vue'

defineOptions({ name: 'AndroidProductView' })

usePageMetadata({
  title: 'SinShield for Android — Block Adult Content Privately',
  description:
    'SinShield blocks adult websites and covers explicit content inside Instagram and X, with every check kept on your phone. No accounts, no tracking, no judgment.',
  path: '/products/android',
  image: '/sinshield-thumbnail.jpg',
})
</script>

<template>
  <main>
    <HeroSection />
    <FeaturesSection />
  </main>
</template>
