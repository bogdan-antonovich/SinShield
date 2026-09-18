<script setup lang="ts">
import BaseContainer from '@/common/components/BaseContainer.vue'
import { usePageMetadata } from '@/common/composables/usePageMetadata'
import TheBreadCrumbs from '@/layouts/components/TheBreadCrumbs.vue'

defineOptions({ name: 'AboutView' })

usePageMetadata({
  title: 'About SinShield | Private Android Protection',
  description:
    'Learn why SinShield exists, how its private Android protection works, what it can and cannot do, and how to contact the team.',
  path: '/about',
  image: '/sinshield-thumbnail.jpg',
})

const breadcrumbs = [{ label: 'Home', to: '/' }, { label: 'About' }]
</script>

<template>
  <main class="bg-background-soft pb-20 pt-28 sm:pb-28 sm:pt-32 lg:pb-32 lg:pt-40">
    <BaseContainer>
      <TheBreadCrumbs :items="breadcrumbs" color="#082D48" class="mb-10 sm:mb-14" />

      <header class="max-w-5xl">
        <p class="font-display text-sm font-bold uppercase tracking-[0.18em] text-primary">About SinShield</p>
        <h1 class="mt-4 font-display text-[2.6rem] font-extrabold leading-[1.02] tracking-[-0.04em] text-navy sm:text-6xl lg:text-[5rem]">
          A private layer of friction between a trigger and your next choice
        </h1>
        <p class="mt-7 max-w-3xl text-lg leading-8 text-navy/70 sm:text-xl sm:leading-9">
          SinShield is an Android adult-content blocker built around a simple idea: a decision made with a clear head should have more support when an unexpected image, familiar feed, or known website appears later.
        </p>
      </header>

      <div class="mt-14 grid gap-6 lg:grid-cols-3 lg:gap-8">
        <article class="rounded-3xl bg-white p-7 ring-1 ring-navy/8 sm:p-8">
          <p class="font-display text-4xl font-extrabold text-primary">On device</p>
          <h2 class="mt-5 font-display text-2xl font-bold text-navy">Private image analysis</h2>
          <p class="mt-3 leading-7 text-navy/70">Visible frames in supported apps are analyzed and discarded on the phone instead of being uploaded to SinShield.</p>
        </article>
        <article class="rounded-3xl bg-white p-7 ring-1 ring-navy/8 sm:p-8">
          <p class="font-display text-4xl font-extrabold text-primary">Two layers</p>
          <h2 class="mt-5 font-display text-2xl font-bold text-navy">Screens and websites</h2>
          <p class="mt-3 leading-7 text-navy/70">Screen-level covering protects supported feeds, while local DNS filtering blocks requests to known adult domains.</p>
        </article>
        <article class="rounded-3xl bg-white p-7 ring-1 ring-navy/8 sm:p-8">
          <p class="font-display text-4xl font-extrabold text-primary">No account</p>
          <h2 class="mt-5 font-display text-2xl font-bold text-navy">Less data by design</h2>
          <p class="mt-3 leading-7 text-navy/70">You can use SinShield without creating a cloud profile, and it does not provide remote activity reports to another person.</p>
        </article>
      </div>

      <div class="mx-auto mt-16 max-w-4xl space-y-12 sm:mt-20">
        <section>
          <h2 class="font-display text-3xl font-extrabold tracking-[-0.03em] text-navy sm:text-4xl">Why we built it</h2>
          <p class="mt-5 text-lg leading-8 text-navy/72">People usually do not need another lecture about a habit they already want to change. They need practical support at the point where a routine becomes automatic, without surrendering a sensitive browsing history or a stream of screenshots to a remote service.</p>
          <p class="mt-5 text-lg leading-8 text-navy/72">SinShield therefore focuses on interruption rather than surveillance. It can cover an unsafe result, reject a known adult-domain request, and create a short pause, but the person using the phone still decides what happens next.</p>
        </section>

        <section>
          <h2 class="font-display text-3xl font-extrabold tracking-[-0.03em] text-navy sm:text-4xl">What SinShield does not promise</h2>
          <p class="mt-5 text-lg leading-8 text-navy/72">SinShield is not therapy, a medical treatment, or a guarantee that every unsafe image or website will be blocked. Automated detection can make mistakes, Android permissions can be revoked, and determined users can change device settings. The app is a practical protection layer, not a claim that technology can make a difficult choice disappear.</p>
        </section>

        <section class="rounded-[2rem] bg-navy p-8 text-white sm:p-10">
          <h2 class="font-display text-3xl font-extrabold tracking-[-0.03em]">How to reach us</h2>
          <p class="mt-4 max-w-2xl text-lg leading-8 text-white/75">Questions, corrections, privacy concerns, and product feedback are welcome at <a href="mailto:support@sinshield.app" class="font-semibold text-white underline underline-offset-4">support@sinshield.app</a>. For how our articles are researched and corrected, read our <a href="/editorial-standards" class="font-semibold text-white underline underline-offset-4">editorial standards</a>.</p>
        </section>
      </div>
    </BaseContainer>
  </main>
</template>
