<script setup lang="ts">
import { usePageMetadata } from '@/common/composables/usePageMetadata'
import HeroSection from '@/modules/products/components/HeroSection.vue'
import StepsSection from '@/modules/products/components/StepsSection.vue';

defineOptions({ name: 'ProductsView' })

usePageMetadata({
  title: 'Get SinShield — Adult Content Blocker for Android',
  description:
    'Choose your platform and get SinShield, the private on-device adult content blocker. Available for Android with no account, no tracking, and no uploaded screenshots.',
  path: '/products',
  image: '/sinshield-thumbnail.jpg',
})
</script>

<template>
  <main>
    <HeroSection />
    <StepsSection />
  </main>
</template>
