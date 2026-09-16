<script setup lang="ts">
import FeaturesSection from '@/modules/android/components/FeaturesSection.vue';
import HeroSection from '@/modules/android/components/HeroSection.vue'

defineOptions({ name: 'AndroidProductView' })
</script>

<template>
  <main>
    <HeroSection />
    <FeaturesSection />
  </main>
</template>
