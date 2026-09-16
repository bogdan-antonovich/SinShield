<script setup lang="ts">
import { onMounted, ref } from 'vue'
import QRCode from 'qrcode'

import BaseButton from '@/common/components/BaseButton.vue'
import BaseContainer from '@/common/components/BaseContainer.vue'
import TheBreadCrumbs from '@/layouts/components/TheBreadCrumbs.vue'

const GOOGLE_PLAY_URL = 'https://play.google.com/store/apps/details?id=com.example.sinshield'
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
  <section id="hero" class="app-shell relative z-40 min-h-dvh">
    <BaseContainer
      class="flex flex-col items-center pb-[195px] pt-[130px] sm:pb-[210px] sm:pt-[140px] md:pb-[120px] md:pt-[180px] lg:pb-[160px] xl:pb-[200px]"
    >
      <TheBreadCrumbs
        :items="breadcrumbs"
        color="#ffffff"
        class="mb-12 self-start sm:mb-16"
      />

      <h1
        class="mb-8 max-w-248.75 text-center text-[32px] font-extrabold leading-[1.15] text-white sm:text-[44px] md:mb-6 md:text-[56px] lg:mb-8 lg:text-[4rem]"
      >
        Private Protection for Your Android Device
      </h1>
      <p
        class="mb-14 max-w-236.75 text-center text-base font-light leading-[1.3] text-white sm:text-xl md:mb-20 md:text-2xl lg:mb-16"
      >
        SinShield blocks adult websites and covers explicit content inside supported apps, while
        every check stays on your phone. It gives you a practical layer of protection without
        accounts, activity tracking, or judgment.
      </p>

      <div class="flex flex-col items-center gap-8">
        <div class="rounded-[1.75rem] bg-white p-3 shadow-xl shadow-navy/20">
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
