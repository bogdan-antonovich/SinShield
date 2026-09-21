<script setup lang="ts">
import { usePageMetadata } from '@/common/composables/usePageMetadata'
import BaseButton from '@/common/components/BaseButton.vue'
import BaseContainer from '@/common/components/BaseContainer.vue'
import HeroSection from '@/modules/products/components/HeroSection.vue'
import StepsSection from '@/modules/products/components/StepsSection.vue';

const GOOGLE_PLAY_URL = 'https://play.google.com/store/apps/details?id=app.sinshield'

defineOptions({ name: 'ProductsView' })

usePageMetadata({
  title: 'Adult Content Blocker for Android | SinShield',
  description:
    'Get SinShield for Android: private screen-level adult-content covering and website blocking with no account or uploaded screenshots.',
  path: '/products',
  image: '/sinshield-thumbnail.jpg',
})
</script>

<template>
  <main>
    <HeroSection />
    <StepsSection />
    <section class="bg-background-soft py-16 sm:py-20 lg:py-28" aria-labelledby="product-fit-heading">
      <!-- <BaseContainer>
        <div class="mx-auto max-w-4xl">
          <h2 id="product-fit-heading" class="mt-4 font-display text-4xl font-extrabold tracking-[-0.04em] text-navy sm:text-5xl">
            Is SinShield the right protection for you?
          </h2>
          <p class="mt-6 text-lg leading-8 text-navy/70">
            If adult content keeps finding its way onto your phone, wanting to stop is only part of
            the answer. SinShield gives you protection at the moments when temptation is easiest to
            reach, so you have a better chance to pause, step away, and stay in control.
          </p>
          <div class="mt-12 space-y-14 sm:mt-14 sm:space-y-16">
            <section aria-labelledby="same-moment-heading">
              <h3
                id="same-moment-heading"
                class="font-display text-2xl font-bold tracking-[-0.02em] text-navy sm:text-3xl"
              >
                You are tired of fighting the same moment
              </h3>
              <ol class="mt-7 grid gap-8 sm:grid-cols-2">
                <li class="relative border-t border-navy/15 pt-8">
                  <span
                    class="absolute -top-5 left-0 flex size-10 items-center justify-center rounded-full bg-white font-display text-xs font-bold text-navy shadow-[0_6px_18px_rgb(8_45_72/0.14)] ring-1 ring-navy/5"
                  >
                    01
                  </span>
                  <img
                    src="/images/how-it-works/content-covered.svg"
                    alt="A shield showing that unsafe content is covered"
                    width="800"
                    height="600"
                    loading="lazy"
                    class="h-28 w-full max-w-44 object-contain"
                  />
                  <ul class="mt-4 list-disc space-y-2 pl-5 text-base leading-7 text-navy/70 marker:text-primary">
                    <li>One unexpected image can pull you toward content you meant to avoid.</li>
                    <li>Adult websites are still too easy to reach when your resolve is low.</li>
                  </ul>
                </li>
                <li class="relative border-t border-navy/15 pt-8">
                  <span
                    class="absolute -top-5 left-0 flex size-10 items-center justify-center rounded-full bg-white font-display text-xs font-bold text-navy shadow-[0_6px_18px_rgb(8_45_72/0.14)] ring-1 ring-navy/5"
                  >
                    02
                  </span>
                  <img
                    src="/images/how-it-works/scan-starts.svg"
                    alt="A protection scan beginning on a phone"
                    width="800"
                    height="600"
                    loading="lazy"
                    class="h-28 w-full max-w-44 object-contain"
                  />
                  <ul class="mt-4 list-disc space-y-2 pl-5 text-base leading-7 text-navy/70 marker:text-primary">
                    <li>You do not want to depend on willpower after temptation has appeared.</li>
                    <li>You want a pause between the urge and the next action.</li>
                  </ul>
                </li>
              </ol>
            </section>

            <section aria-labelledby="private-support-heading">
              <h3
                id="private-support-heading"
                class="font-display text-2xl font-bold tracking-[-0.02em] text-navy sm:text-3xl"
              >
                You want support that still feels like yours
              </h3>
              <ol class="mt-7 grid gap-8 sm:grid-cols-2">
                <li class="relative border-t border-navy/15 pt-8">
                  <span
                    class="absolute -top-5 left-0 flex size-10 items-center justify-center rounded-full bg-white font-display text-xs font-bold text-navy shadow-[0_6px_18px_rgb(8_45_72/0.14)] ring-1 ring-navy/5"
                  >
                    03
                  </span>
                  <img
                    src="/images/about/no-data.svg"
                    alt="A phone keeping personal information private"
                    width="648"
                    height="633"
                    loading="lazy"
                    class="h-28 w-full max-w-44 object-contain"
                  />
                  <ul class="mt-4 list-disc space-y-2 pl-5 text-base leading-7 text-navy/70 marker:text-primary">
                    <li>You want protection to step in quietly while you use your phone.</li>
                    <li>You want your screenshots and browsing activity to stay private.</li>
                  </ul>
                </li>
                <li class="relative border-t border-navy/15 pt-8">
                  <span
                    class="absolute -top-5 left-0 flex size-10 items-center justify-center rounded-full bg-white font-display text-xs font-bold text-navy shadow-[0_6px_18px_rgb(8_45_72/0.14)] ring-1 ring-navy/5"
                  >
                    04
                  </span>
                  <img
                    src="/images/editorial/informed-decision.svg"
                    alt="A person making an informed choice"
                    width="800"
                    height="600"
                    loading="lazy"
                    class="h-28 w-full max-w-44 object-contain"
                  />
                  <ul class="mt-4 list-disc space-y-2 pl-5 text-base leading-7 text-navy/70 marker:text-primary">
                    <li>You want to choose how strict your protection should be.</li>
                    <li>You want practical help without judgment, surveillance, or reports.</li>
                  </ul>
                </li>
              </ol>
            </section>
          </div>
          <div class="mt-10 border-t border-navy/10 pt-8">
            <p class="max-w-3xl text-lg font-semibold leading-8 text-navy">
              If these moments feel familiar, you do not need another promise to try harder. You
              need protection that is already there when the difficult moment arrives. That is what
              SinShield is built to give you.
            </p>
            <BaseButton :href="GOOGLE_PLAY_URL" class="mt-8 w-full sm:w-fit">
              Get SinShield for Android
            </BaseButton>
          </div>
        </div>
      </BaseContainer> -->
    </section>
  </main>
</template>
