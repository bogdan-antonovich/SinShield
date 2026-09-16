<script setup lang="ts">
import BaseContainer from '@/common/components/BaseContainer.vue'

const protectionTypes = [
  {
    label: 'X',
    icon: '/icons/x.svg',
  },
  {
    label: 'Instagram',
    icon: '/icons/instagram.svg',
  },
  {
    label: 'Adult Websites',
    icon: '/icons/globe-alt.svg',
  },
]
</script>

<template>
  <section id="about_protection" class="app-shell py-20 sm:py-24 lg:py-40">
    <BaseContainer class="flex flex-col items-center">
      <h2
        class="max-w-6xl text-center font-display text-[40px] font-extrabold leading-[1.05] tracking-[-0.035em] text-white sm:text-[56px] lg:text-[80px]"
      >
        Where SinShield Protects You
      </h2>

      <div class="mt-16 w-full max-w-6xl sm:mt-20 lg:mt-24">
        <div class="grid grid-cols-1 gap-12 md:grid-cols-3 md:gap-8 lg:gap-16">
          <div
            v-for="protectionType in protectionTypes"
            :key="protectionType.label"
            class="group flex flex-col items-center gap-7 rounded-xl p-2 text-center transition duration-200 ease-out hover:-translate-y-1 hover:scale-105 lg:gap-9"
          >
            <img
              :src="protectionType.icon"
              alt=""
              aria-hidden="true"
              class="h-24 w-28 object-contain opacity-80 transition-opacity duration-200 group-hover:opacity-100 sm:h-32 sm:w-36 lg:h-44 lg:w-48"
            />
            <span
              class="font-display text-2xl font-bold leading-tight text-white/70 transition-colors duration-200 group-hover:text-white sm:text-[28px] lg:text-[32px]"
            >
              {{ protectionType.label }}
            </span>
          </div>
        </div>
      </div>
    </BaseContainer>
  </section>
</template>
