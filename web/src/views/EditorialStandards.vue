<script setup lang="ts">
import BaseContainer from '@/common/components/BaseContainer.vue'
import { usePageMetadata } from '@/common/composables/usePageMetadata'
import TheBreadCrumbs from '@/layouts/components/TheBreadCrumbs.vue'

defineOptions({ name: 'EditorialStandardsView' })

usePageMetadata({
  title: 'Editorial Standards and Corrections | SinShield',
  description:
    'How SinShield researches, reviews, updates, discloses conflicts, and corrects articles about digital habits and adult-content protection.',
  path: '/editorial-standards',
  image: '/sinshield-thumbnail.jpg',
})

const breadcrumbs = [
  { label: 'Home', to: '/' },
  { label: 'Editorial standards' },
]
</script>

<template>
  <main class="bg-background-soft pb-20 pt-28 sm:pb-28 sm:pt-32 lg:pb-32 lg:pt-40">
    <BaseContainer>
      <TheBreadCrumbs :items="breadcrumbs" color="#082D48" class="mb-10 sm:mb-14" />
      <header class="max-w-5xl">
        <p class="font-display text-sm font-bold uppercase tracking-[0.18em] text-primary">Trust and transparency</p>
        <h1 class="mt-4 font-display text-[2.6rem] font-extrabold leading-[1.02] tracking-[-0.04em] text-navy sm:text-6xl lg:text-[5rem]">SinShield editorial standards</h1>
        <p class="mt-7 max-w-3xl text-lg leading-8 text-navy/70 sm:text-xl sm:leading-9">Our articles are written to help readers make practical decisions without shame, inflated claims, or disguised advertising. This page explains the standard we apply before publication and after an article goes live.</p>
      </header>

      <div class="mt-14 grid gap-6 md:grid-cols-2">
        <section class="rounded-3xl bg-white p-7 ring-1 ring-navy/8 sm:p-9">
          <h2 class="font-display text-2xl font-bold text-navy">Evidence comes before certainty</h2>
          <p class="mt-4 text-base leading-7 text-navy/70">Research claims should link to primary studies, systematic reviews, or official guidance whenever those sources are available. We distinguish correlation from causation, avoid turning a preliminary finding into a universal rule, and state when the evidence is mixed or limited.</p>
        </section>
        <section class="rounded-3xl bg-white p-7 ring-1 ring-navy/8 sm:p-9">
          <h2 class="font-display text-2xl font-bold text-navy">Current facts use current sources</h2>
          <p class="mt-4 text-base leading-7 text-navy/70">Platform policies, prices, settings, product availability, and laws can change. We prefer official policies and product documentation, date time-sensitive claims, and encourage local professional advice where an error could have legal or health consequences.</p>
        </section>
        <section class="rounded-3xl bg-white p-7 ring-1 ring-navy/8 sm:p-9">
          <h2 class="font-display text-2xl font-bold text-navy">Product ownership is disclosed</h2>
          <p class="mt-4 text-base leading-7 text-navy/70">SinShield articles may recommend SinShield, which is our product. Comparisons must disclose that relationship, give competing tools credit for jobs they perform better, and describe meaningful SinShield limitations instead of presenting promotion as neutral analysis.</p>
        </section>
        <section class="rounded-3xl bg-white p-7 ring-1 ring-navy/8 sm:p-9">
          <h2 class="font-display text-2xl font-bold text-navy">Readers keep their agency</h2>
          <p class="mt-4 text-base leading-7 text-navy/70">We do not diagnose readers, promise a cure, moralize about an unwanted exposure, or claim that software makes relapse impossible. Technology can add friction and support a decision; it cannot make the decision on someone’s behalf.</p>
        </section>
      </div>

      <div class="mx-auto mt-16 max-w-4xl space-y-12 sm:mt-20">
        <section>
          <h2 class="font-display text-3xl font-extrabold tracking-[-0.03em] text-navy sm:text-4xl">Our publication process</h2>
          <ol class="mt-6 space-y-5 text-lg leading-8 text-navy/72">
            <li><strong class="text-navy">1. Define the reader’s decision.</strong> Each article begins with the specific question a reader needs answered and the practical consequence of getting it wrong.</li>
            <li><strong class="text-navy">2. Gather reliable sources.</strong> We prioritize official documentation, primary research, systematic reviews, and authoritative public bodies over unsourced summaries.</li>
            <li><strong class="text-navy">3. Check claims and limitations.</strong> Material claims are checked against their sources, time-sensitive details are reviewed, and the article must explain important uncertainty or product limitations.</li>
            <li><strong class="text-navy">4. Review for clarity and harm.</strong> We remove shame-based framing, unsupported certainty, hidden commercial bias, and language that takes agency away from the reader.</li>
            <li><strong class="text-navy">5. Update when facts change.</strong> Articles may be revised when research, laws, platform rules, or product capabilities change. A substantive revision should receive a visible updated date.</li>
          </ol>
        </section>

        <section>
          <h2 class="font-display text-3xl font-extrabold tracking-[-0.03em] text-navy sm:text-4xl">Corrections</h2>
          <p class="mt-5 text-lg leading-8 text-navy/72">If you find a factual error, broken source, outdated policy, or missing disclosure, email <a href="mailto:support@sinshield.app" class="font-semibold text-primary underline underline-offset-4">support@sinshield.app</a> with the article URL and the claim in question. We review correction requests against the best available source and update material errors rather than quietly defending them.</p>
        </section>

        <section class="rounded-[2rem] bg-navy p-8 text-white sm:p-10">
          <h2 class="font-display text-3xl font-extrabold tracking-[-0.03em]">Who publishes the blog?</h2>
          <p class="mt-4 text-lg leading-8 text-white/75">Articles are published by <a href="/authors/sinshield-editorial" class="font-semibold text-white underline underline-offset-4">SinShield Editorial</a>, the product editorial function responsible for research, source checking, product disclosures, and corrections. Where a topic requires licensed medical or legal judgment, our articles provide general information and direct readers to an appropriately qualified professional.</p>
        </section>
      </div>
    </BaseContainer>
  </main>
</template>
