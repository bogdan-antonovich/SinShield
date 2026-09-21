<script setup lang="ts">
import { usePageMetadata } from '@/common/composables/usePageMetadata'
import FeaturesSection from '@/modules/android/components/FeaturesSection.vue'
import HeroSection from '@/modules/android/components/HeroSection.vue'
import FaqSection from '@/modules/main/components/FaqSection.vue'

defineOptions({ name: 'AndroidProductView' })

usePageMetadata({
  title: 'Private Adult Content Blocker for Android | SinShield',
  description:
    'SinShield privately covers explicit content in supported Android apps and blocks known adult websites, with no account or uploaded screenshots.',
  path: '/products/android',
  image: '/sinshield-thumbnail.jpg',
})

const faqItems = [
  {
    question: 'Does SinShield upload screenshots?',
    answer:
      'No. Visible frames are analyzed by models running on your Android device and discarded there; SinShield does not receive those frames or use them to build an activity history.',
  },
  {
    question: 'Which apps does it protect?',
    answer:
      'Screen-level covering currently focuses on Instagram and X. A separate local DNS filter can block known adult domains opened from a browser or another app, and you can add domains to a personal block list.',
  },
  {
    question: 'Can it guarantee that nothing gets through?',
    answer:
      'No automated blocker can make that guarantee. SinShield adds friction and reduces easy access, but detection can occasionally miss unsafe content or cover a safe image, so it works best as one practical layer in a broader plan.',
  },
]
</script>

<template>
  <main>
    <HeroSection />
    <FeaturesSection />
    <FaqSection
      title="What should you know before using SinShield?"
      section-id="android-faq"
      :items="faqItems"
    />
  </main>
</template>
