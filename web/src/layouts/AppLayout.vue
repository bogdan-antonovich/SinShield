<script setup lang="ts">
import { RouterView } from 'vue-router'

import TheHeader from '@/layouts/components/TheHeader.vue'
import TheFooter from './components/TheFooter.vue';
</script>

<template>
  <TheHeader />
    <RouterView />
  <TheFooter />
</template>
