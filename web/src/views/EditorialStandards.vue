<script setup lang="ts">
import BaseButton from '@/common/components/BaseButton.vue'
import BaseContainer from '@/common/components/BaseContainer.vue'
import InteractiveShowcaseSection from '@/common/components/InteractiveShowcaseSection.vue'
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

interface EditorialPrinciple {
  title: string
  description: string
  illustration: string
  illustrationAlt: string
  imageFit?: 'cover' | 'contain'
}

interface PublicationStep {
  title: string
  description: string
  illustration: string
  illustrationAlt: string
}

const breadcrumbs = [{ label: 'Home', to: '/' }, { label: 'Editorial standards' }]
const principles: EditorialPrinciple[] = [
  {
    title: 'Evidence before certainty',
    description:
      'We look for primary studies, systematic reviews, and official guidance. If the evidence is mixed or limited, we say so.',
    illustration: '/images/editorial/researching.svg',
    illustrationAlt: 'A researcher examining information across several sources',
    imageFit: 'contain',
  },
  {
    title: 'Current facts, current sources',
    description:
      'Policies, prices, settings, laws, and product features change. We check time-sensitive claims against current, official sources.',
    illustration: '/images/editorial/verified.svg',
    illustrationAlt: 'A person reviewing verified information',
    imageFit: 'contain',
  },
  {
    title: 'Clear product disclosure',
    description:
      'SinShield is our product. When we recommend it, we disclose that relationship and explain its meaningful limitations.',
    illustration: '/images/editorial/disclosure.svg',
    illustrationAlt: 'Two people reviewing an agreement together',
    imageFit: 'contain',
  },
  {
    title: 'Your choice stays yours',
    description:
      'We do not diagnose, shame, or promise a cure. Technology can support a decision, but it cannot make that decision for you.',
    illustration: '/images/editorial/informed-decision.svg',
    illustrationAlt: 'A person weighing information before making a decision',
    imageFit: 'contain',
  },
]
const publicationSteps: PublicationStep[] = [
  {
    title: 'Start with your question',
    description:
      'Every article begins with the decision you need to make and what could happen if the answer is unclear or wrong.',
    illustration: '/images/editorial/looking-for-answers.svg',
    illustrationAlt: 'A person looking for a clear answer',
  },
  {
    title: 'Find reliable sources',
    description:
      'We look first for official documentation, primary research, systematic reviews, and authoritative public bodies.',
    illustration: '/images/editorial/researching.svg',
    illustrationAlt: 'A researcher comparing several sources',
  },
  {
    title: 'Check the claims',
    description:
      'Important facts, time-sensitive details, uncertainty, and product limitations are checked before publication.',
    illustration: '/images/editorial/verified.svg',
    illustrationAlt: 'A person checking verified information',
  },
  {
    title: 'Review the language',
    description:
      'We remove shame, unsupported certainty, hidden commercial bias, and wording that takes away your agency.',
    illustration: '/images/editorial/document-review.svg',
    illustrationAlt: 'A person carefully reviewing a document',
  },
  {
    title: 'Keep it current',
    description:
      'When research, laws, platform rules, or product capabilities change, substantive revisions receive a visible updated date.',
    illustration: '/images/editorial/system-update.svg',
    illustrationAlt: 'A person updating information on a screen',
  },
]
</script>

<template>
  <main>
    <section id="hero" class="app-shell relative z-40 min-h-[72svh]">
      <BaseContainer
        class="flex min-h-[72svh] flex-col items-center justify-center pb-20 pt-28 sm:pb-24 sm:pt-32 md:pb-28 md:pt-36 lg:pb-32 lg:pt-40"
      >
        <TheBreadCrumbs :items="breadcrumbs" color="#ffffff" class="mb-10 self-start sm:mb-14" />

        <header class="flex flex-col items-center">
          <h1
            class="mb-6 max-w-248.75 text-center text-[clamp(2.1rem,9.5vw,3rem)] font-extrabold leading-[1.08] tracking-[-0.025em] text-white sm:text-[44px] md:text-[56px] lg:mb-8 lg:text-[4rem]"
          >
            Our Editorial Standards
          </h1>
          <p
            class="max-w-236.75 text-center text-[17px] font-normal leading-[1.5] text-white/85 sm:text-xl md:text-2xl"
          >
            We write to help you make informed choices—not to shame you, scare you, or sell you a
            promise we cannot keep. Here is how every SinShield article is researched, reviewed, and
            corrected.
          </p>
          <BaseButton to="/blog" variant="inverse" class="mt-10 w-full sm:w-fit">
            Read Our Guides
          </BaseButton>
        </header>
      </BaseContainer>
    </section>

    <InteractiveShowcaseSection
      id="editorial-principles"
      heading="What Every Article Has to Earn"
      description="Trust starts with being clear about the evidence, our interests, and the limits of what we know. These are the standards we use before anything goes live."
      :items="principles"
      tablist-label="SinShield editorial principles"
      theme="light"
      cta-label="Read Our Guides"
      cta-to="/blog"
    />

    <section class="about-footer-blend py-16 sm:py-20 lg:py-32">
      <BaseContainer>
        <div class="mx-auto max-w-4xl space-y-12 sm:space-y-16">
          <section aria-labelledby="publication-process-heading">
            <header class="text-center">
              <h2
                id="publication-process-heading"
                class="mt-4 font-display text-3xl font-extrabold tracking-[-0.03em] text-navy sm:text-4xl"
              >
                How an Article Gets Published
              </h2>
            </header>

            <div class="relative mt-14 sm:mt-16">
              <span
                aria-hidden="true"
                class="absolute bottom-6 left-5 top-6 w-px bg-navy/15 md:left-1/2 md:-translate-x-px"
              />

              <ol class="space-y-16 sm:space-y-20 md:space-y-10">
                <li
                  v-for="(step, index) in publicationSteps"
                  :key="step.title"
                  class="relative pl-16 md:grid md:min-h-64 md:grid-cols-[minmax(0,1fr)_5rem_minmax(0,1fr)] md:items-center md:gap-8 md:pl-0"
                >
                  <span
                    class="absolute left-0 top-5 z-10 flex size-10 items-center justify-center rounded-full bg-white font-display text-xs font-bold text-navy shadow-[0_6px_18px_rgb(8_45_72/0.14)] ring-1 ring-navy/5 md:left-1/2 md:top-1/2 md:-translate-x-1/2 md:-translate-y-1/2"
                  >
                    {{ String(index + 1).padStart(2, '0') }}
                  </span>

                  <div
                    class="flex min-h-44 items-center justify-center md:min-h-64"
                    :class="
                      index % 2 === 0
                        ? 'md:col-start-1 md:row-start-1'
                        : 'md:col-start-3 md:row-start-1'
                    "
                  >
                    <img
                      :src="step.illustration"
                      :alt="step.illustrationAlt"
                      width="360"
                      height="260"
                      loading="lazy"
                      class="h-44 w-full max-w-72 object-contain sm:h-52 md:h-56"
                    />
                  </div>

                  <div
                    class="mt-5 md:mt-0"
                    :class="
                      index % 2 === 0
                        ? 'md:col-start-3 md:row-start-1'
                        : 'md:col-start-1 md:row-start-1 md:text-right'
                    "
                  >
                    <h3 class="font-display text-2xl font-bold tracking-[-0.02em] text-navy">
                      {{ step.title }}
                    </h3>
                    <p class="mt-3 text-base leading-7 text-navy/70 sm:text-lg sm:leading-8">
                      {{ step.description }}
                    </p>
                  </div>
                </li>
              </ol>
            </div>
          </section>

          <section>
            <h2
              class="font-display text-3xl font-extrabold tracking-[-0.03em] text-navy sm:text-4xl"
            >
              Corrections
            </h2>
            <p class="mt-5 text-lg leading-8 text-navy/72">
              Found a factual error, broken source, outdated policy, or missing disclosure? Email
              <a
                href="mailto:support@sinshield.app?subject=Editorial%20correction"
                class="font-semibold text-primary underline underline-offset-4"
                >support@sinshield.app</a
              >
              with the article link and the claim in question. We check it against the best
              available source and correct material errors clearly.
            </p>
          </section>

          <section class="pt-10 text-white sm:pt-16">
            <h2 class="font-display text-3xl font-extrabold tracking-[-0.03em] sm:text-4xl">
              Who writes the guides?
            </h2>
            <p class="mt-5 text-lg leading-8 text-white/75">
              Articles are published by
              <a
                href="/authors/sinshield-editorial"
                class="font-semibold text-white underline underline-offset-4"
                >SinShield Editorial</a
              >, the team responsible for research, source checking, product disclosures, updates,
              and corrections. When a topic needs licensed medical or legal judgment, we provide
              general information and point you toward a qualified professional.
            </p>
          </section>
        </div>
      </BaseContainer>
    </section>
  </main>
</template>
