<script setup lang="ts">

import BaseContainer from '@/common/components/BaseContainer.vue'
import TheBreadCrumbs from '@/layouts/components/TheBreadCrumbs.vue'

const breadcrumbs = [
  { label: 'Home', to: '/' },
  { label: 'Products' },
]
</script>

<template>
  <section id="hero" class="app-shell relative z-40" >
    <BaseContainer
      class="flex flex-col items-center pb-[195px] pt-[130px] sm:pb-[210px] sm:pt-[140px] md:pb-[120px] md:pt-[180px] lg:pb-[160px] xl:pb-[200px]"
    >
      <TheBreadCrumbs
        :items="breadcrumbs"
        color="#ffffff"
        class="mb-12 self-start sm:mb-16"
      />

      <h1 class="mb-12 max-w-248.75 text-center text-[32px] font-extrabold leading-[1.15] text-white sm:text-[44px] md:mb-6 md:text-[56px] lg:mb-8 lg:text-[4rem]">
        Where Would You Like to Start?
      </h1>

      <!-- Platform tile: transparent (parent color), lightens + grows on hover -->
      <RouterLink
        to="/products/android"
        aria-label="SinShield for Android"
        class="group inline-flex flex-col items-center gap-3 rounded-xl p-1 opacity-90 transition duration-200 ease-out hover:-translate-y-1 hover:scale-105 hover:opacity-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/40"
      >
        <span
          aria-hidden="true"
          class="h-15 w-20.25 bg-white"
          style="
            mask: url('/icons/android-robot.svg') center / contain no-repeat;
            -webkit-mask: url('/icons/android-robot.svg') center / contain no-repeat;
          "
        />
        <span class="font-display font-bold text-white">Android</span>
      </RouterLink>
    </BaseContainer>
  </section>
</template>
