<script setup lang="ts">
import { usePageMetadata } from '@/common/composables/usePageMetadata'
import BaseContainer from '@/common/components/BaseContainer.vue'
import FeaturesSection from '@/modules/android/components/FeaturesSection.vue';
import HeroSection from '@/modules/android/components/HeroSection.vue'

defineOptions({ name: 'AndroidProductView' })

usePageMetadata({
  title: 'Private Adult Content Blocker for Android | SinShield',
  description:
    'SinShield privately covers explicit content in supported Android apps and blocks known adult websites, with no account or uploaded screenshots.',
  path: '/products/android',
  image: '/sinshield-thumbnail.jpg',
})
</script>

<template>
  <main>
    <HeroSection />
    <FeaturesSection />
    <section class="bg-background-soft py-16 sm:py-20 lg:py-28" aria-labelledby="android-questions-heading">
      <BaseContainer>
        <div class="mx-auto max-w-4xl">
          <p class="font-display text-sm font-bold uppercase tracking-[0.18em] text-primary">Clear answers</p>
          <h2 id="android-questions-heading" class="mt-4 font-display text-4xl font-extrabold tracking-[-0.04em] text-navy sm:text-5xl">
            What should you know before using SinShield?
          </h2>
          <div class="mt-10 space-y-5">
            <article class="rounded-3xl bg-white p-7 ring-1 ring-navy/8 sm:p-8">
              <h3 class="font-display text-2xl font-bold text-navy">Does SinShield upload screenshots?</h3>
              <p class="mt-3 text-lg leading-8 text-navy/70">No. Visible frames are analyzed by models running on your Android device and discarded there; SinShield does not receive those frames or use them to build an activity history.</p>
            </article>
            <article class="rounded-3xl bg-white p-7 ring-1 ring-navy/8 sm:p-8">
              <h3 class="font-display text-2xl font-bold text-navy">Which apps does it protect?</h3>
              <p class="mt-3 text-lg leading-8 text-navy/70">Screen-level covering currently focuses on Instagram and X. A separate local DNS filter can block known adult domains opened from a browser or another app, and you can add domains to a personal block list.</p>
            </article>
            <article class="rounded-3xl bg-white p-7 ring-1 ring-navy/8 sm:p-8">
              <h3 class="font-display text-2xl font-bold text-navy">Can it guarantee that nothing gets through?</h3>
              <p class="mt-3 text-lg leading-8 text-navy/70">No automated blocker can make that guarantee. SinShield adds friction and reduces easy access, but detection can occasionally miss unsafe content or cover a safe image, so it works best as one practical layer in a broader plan.</p>
            </article>
          </div>
          <p class="mt-8 text-base leading-7 text-navy/65">
            For setup and permissions, see the <a href="/#faq" class="font-semibold text-primary underline underline-offset-4">full FAQ</a>. For independent decision support, browse the <a href="/blog" class="font-semibold text-primary underline underline-offset-4">SinShield guides</a>.
          </p>
        </div>
      </BaseContainer>
    </section>
  </main>
</template>
