<script setup lang="ts">
import HeroSection from '@/modules/products/components/HeroSection.vue'
import StepsSection from '@/modules/products/components/StepsSection.vue';

defineOptions({ name: 'HomeView' })
</script>

<template>
  <main>
    <HeroSection />
    <StepsSection />
  </main>
</template>
