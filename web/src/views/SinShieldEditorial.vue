<script setup lang="ts">
import BaseContainer from '@/common/components/BaseContainer.vue'
import { usePageMetadata } from '@/common/composables/usePageMetadata'
import TheBreadCrumbs from '@/layouts/components/TheBreadCrumbs.vue'

defineOptions({ name: 'SinShieldEditorialView' })

usePageMetadata({
  title: 'SinShield Editorial | Author and Review Profile',
  description:
    'Meet the SinShield editorial function responsible for researching, reviewing, disclosing, updating, and correcting the SinShield blog.',
  path: '/authors/sinshield-editorial',
  image: '/sinshield-thumbnail.jpg',
})

const breadcrumbs = [
  { label: 'Home', to: '/' },
  { label: 'Blog', to: '/blog' },
  { label: 'SinShield Editorial' },
]
</script>

<template>
  <main class="bg-background-soft pb-20 pt-28 sm:pb-28 sm:pt-32 lg:pb-32 lg:pt-40">
    <BaseContainer>
      <TheBreadCrumbs :items="breadcrumbs" color="#082D48" class="mb-10 sm:mb-14" />
      <div class="grid gap-10 lg:grid-cols-[16rem_minmax(0,1fr)] lg:gap-16">
        <aside>
          <div class="grid aspect-square place-items-center rounded-[2rem] bg-navy p-8 shadow-xl shadow-primary/10">
            <img src="/sinshield-thumbnail.png" alt="SinShield shield mark" class="w-full rounded-3xl" />
          </div>
        </aside>
        <article class="max-w-4xl">
          <p class="font-display text-sm font-bold uppercase tracking-[0.18em] text-primary">Author and reviewer profile</p>
          <h1 class="mt-4 font-display text-[2.6rem] font-extrabold leading-[1.02] tracking-[-0.04em] text-navy sm:text-6xl lg:text-[5rem]">SinShield Editorial</h1>
          <p class="mt-7 text-lg leading-8 text-navy/70 sm:text-xl sm:leading-9">SinShield Editorial is the product editorial function behind the SinShield blog. It combines product knowledge with source-led research to publish practical guidance about adult-content protection, platform controls, digital habits, and the limits of blocking technology.</p>

          <h2 class="mt-12 font-display text-3xl font-extrabold tracking-[-0.03em] text-navy">What this byline means</h2>
          <p class="mt-5 text-lg leading-8 text-navy/72">An organizational byline is used because these articles are produced and maintained as part of the SinShield product publication rather than presented as the independent opinion of a licensed clinician or lawyer. It also makes the commercial relationship clear: SinShield is our product, and recommendations involving it must disclose that fact.</p>

          <h2 class="mt-12 font-display text-3xl font-extrabold tracking-[-0.03em] text-navy">How articles are reviewed</h2>
          <ul class="mt-6 list-disc space-y-4 pl-6 text-lg leading-8 text-navy/72 marker:text-primary">
            <li>Product and privacy claims are checked against how the current Android app works.</li>
            <li>Research claims are traced to the cited paper, review, or authoritative guidance.</li>
            <li>Platform rules, prices, laws, and settings are checked against current official sources.</li>
            <li>Comparisons disclose SinShield ownership and describe meaningful alternatives and limitations.</li>
            <li>Corrections are reviewed through the process described in our editorial standards.</li>
          </ul>

          <div class="mt-12 rounded-[2rem] bg-white p-8 ring-1 ring-navy/8 sm:p-10">
            <h2 class="font-display text-3xl font-extrabold tracking-[-0.03em] text-navy">Contact the editorial team</h2>
            <p class="mt-4 text-lg leading-8 text-navy/70">Send source suggestions, corrections, or questions to <a href="mailto:support@sinshield.app" class="font-semibold text-primary underline underline-offset-4">support@sinshield.app</a>. You can also read the complete <a href="/editorial-standards" class="font-semibold text-primary underline underline-offset-4">editorial standards</a> or browse all <a href="/blog" class="font-semibold text-primary underline underline-offset-4">SinShield guides</a>.</p>
          </div>
        </article>
      </div>
    </BaseContainer>
  </main>
</template>
