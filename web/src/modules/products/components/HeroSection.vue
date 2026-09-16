<script setup lang="ts">
import { RouterLink } from 'vue-router'
import BaseContainer from '@/common/components/BaseContainer.vue'
import TheBreadCrumbs from '@/layouts/components/TheBreadCrumbs.vue'

const breadcrumbs = [
  { label: 'Home', to: '/' },
  { label: 'Products' },
]
</script>

<template>
  <section id="hero" class="app-shell relative z-40 min-h-[72svh]" >
    <BaseContainer
      class="flex min-h-[72svh] flex-col items-center justify-center pb-20 pt-28 sm:pb-24 sm:pt-32 md:pb-28 md:pt-36 lg:pb-32 lg:pt-40"
    >
      <TheBreadCrumbs
        :items="breadcrumbs"
        color="#ffffff"
        class="mb-10 self-start sm:mb-14"
      />

      <h1 class="mb-10 max-w-248.75 text-center text-[clamp(2.1rem,9.5vw,3rem)] font-extrabold leading-[1.08] tracking-[-0.025em] text-white sm:text-[44px] md:text-[56px] lg:text-[4rem]">
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
