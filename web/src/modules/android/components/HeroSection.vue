<script setup lang="ts">
import { onMounted, ref } from 'vue'
import QRCode from 'qrcode'

import BaseButton from '@/common/components/BaseButton.vue'
import BaseContainer from '@/common/components/BaseContainer.vue'
import TheBreadCrumbs from '@/layouts/components/TheBreadCrumbs.vue'

const GOOGLE_PLAY_URL = 'https://play.google.com/store/apps/details?id=app.sinshield'
const qrCodeDataUrl = ref('')
const breadcrumbs = [
  { label: 'Home', to: '/' },
  { label: 'Products', to: '/products' },
  { label: 'Android' },
]

onMounted(async () => {
  qrCodeDataUrl.value = await QRCode.toDataURL(GOOGLE_PLAY_URL, {
    color: {
      dark: '#002945',
      light: '#ffffff',
    },
    errorCorrectionLevel: 'H',
    margin: 2,
    width: 224,
  })
})
</script>

<template>
  <section id="hero" class="app-shell relative z-40 min-h-[100svh]">
    <BaseContainer
      class="flex min-h-[100svh] flex-col items-center justify-center pb-20 pt-28 sm:pb-24 sm:pt-32 md:pb-28 md:pt-36 lg:pb-32 lg:pt-40"
    >
      <TheBreadCrumbs
        :items="breadcrumbs"
        color="#ffffff"
        class="mb-10 self-start sm:mb-14"
      />

      <h1
        class="mb-6 max-w-248.75 text-center text-[clamp(2.1rem,9.5vw,3rem)] font-extrabold leading-[1.08] tracking-[-0.025em] text-white sm:text-[44px] md:text-[56px] lg:mb-8 lg:text-[4rem]"
      >
        Private Protection for Your Android Device
      </h1>
      <p
        class="mb-10 max-w-236.75 text-center text-[17px] font-normal leading-[1.5] text-white/85 sm:mb-12 sm:text-xl md:text-2xl lg:mb-14"
      >
        SinShield blocks adult websites and covers explicit content inside supported apps, while
        every check stays on your phone. It gives you a practical layer of protection without
        accounts, activity tracking, or judgment.
      </p>

      <div class="flex flex-col items-center gap-7">
        <div class="hidden rounded-[1.75rem] bg-white p-3 shadow-xl shadow-navy/20 sm:block">
          <img
            v-if="qrCodeDataUrl"
            :src="qrCodeDataUrl"
            alt="QR code for the SinShield Google Play page"
            class="size-44 select-none rounded-2xl sm:size-48"
            draggable="false"
          />
          <span class="block pt-2 text-center text-sm font-semibold text-navy/70"
            >Scan to install</span
          >
        </div>

        <BaseButton
          :href="GOOGLE_PLAY_URL"
          variant="inverse"
          class="gap-3"
          aria-label="Get it on Google Play"
        >
          <span>Get it on</span>
          <img src="/icons/google-play.svg" alt="" aria-hidden="true" class="h-6 w-auto" />
        </BaseButton>
      </div>
    </BaseContainer>
  </section>
</template>
