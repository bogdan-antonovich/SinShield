<script setup lang="ts">
import { usePageMetadata } from '@/common/composables/usePageMetadata'
import BaseContainer from '@/common/components/BaseContainer.vue'
import HeroSection from '@/modules/products/components/HeroSection.vue'
import StepsSection from '@/modules/products/components/StepsSection.vue';

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
      <BaseContainer>
        <div class="mx-auto max-w-4xl">
          <p class="font-display text-sm font-bold uppercase tracking-[0.18em] text-primary">Before you install</p>
          <h2 id="product-fit-heading" class="mt-4 font-display text-4xl font-extrabold tracking-[-0.04em] text-navy sm:text-5xl">
            Is SinShield the right protection for you?
          </h2>
          <p class="mt-6 text-lg leading-8 text-navy/70">
            SinShield is built for Android users who want private, automatic friction between an unexpected trigger and the next action. It analyzes visible content in supported apps on the device and separately blocks requests to known adult websites, so the two protection layers cover different routes without creating a cloud activity profile.
          </p>
          <div class="mt-10 grid gap-5 sm:grid-cols-2">
            <article class="rounded-3xl bg-white p-7 ring-1 ring-navy/8 sm:p-8">
              <h3 class="font-display text-2xl font-bold text-navy">A good fit when</h3>
              <ul class="mt-5 list-disc space-y-3 pl-5 text-base leading-7 text-navy/70 marker:text-primary">
                <li>You use Android 11 or newer.</li>
                <li>You want image analysis to stay on your phone.</li>
                <li>You need protection in Instagram, X, and the browser.</li>
                <li>You prefer no account and no partner reports.</li>
              </ul>
            </article>
            <article class="rounded-3xl bg-white p-7 ring-1 ring-navy/8 sm:p-8">
              <h3 class="font-display text-2xl font-bold text-navy">Know the limits</h3>
              <ul class="mt-5 list-disc space-y-3 pl-5 text-base leading-7 text-navy/70 marker:text-primary">
                <li>SinShield is not currently available for iPhone.</li>
                <li>Automatic detection can miss content or cover a safe image.</li>
                <li>It does not send accountability reports to another person.</li>
                <li>Android permits only one local VPN at a time.</li>
              </ul>
            </article>
          </div>
          <p class="mt-8 text-base leading-7 text-navy/65">
            Need the technical details first? Read how <a href="/products/android" class="font-semibold text-primary underline underline-offset-4">SinShield for Android works</a>, or review our <a href="/privacy-policy" class="font-semibold text-primary underline underline-offset-4">privacy policy</a> before installing.
          </p>
        </div>
      </BaseContainer>
    </section>
  </main>
</template>
