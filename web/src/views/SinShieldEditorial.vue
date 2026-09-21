<script setup lang="ts">
import BaseContainer from '@/common/components/BaseContainer.vue'
import { usePageMetadata } from '@/common/composables/usePageMetadata'
import TheBreadCrumbs from '@/layouts/components/TheBreadCrumbs.vue'
import { sinShieldEditorial } from '@/modules/main/authors'

defineOptions({ name: 'SinShieldEditorialView' })

usePageMetadata({
  title: 'SinShield Editorial | Author and Review Profile',
  description:
    'Meet the SinShield author responsible for researching, reviewing, disclosing, updating, and correcting every SinShield guide.',
  path: sinShieldEditorial.path,
  image: '/sinshield-thumbnail.jpg',
})

const breadcrumbs = [
  { label: 'Home', to: '/' },
  { label: 'Authors', to: '/authors' },
  { label: sinShieldEditorial.name },
]

const reviewSteps = [
  {
    title: 'Check the product',
    description:
      'Product and privacy claims are checked against how the current Android app works.',
    illustration: '/images/editorial/verified.svg',
    illustrationAlt: 'A person checking verified product information',
  },
  {
    title: 'Trace the research',
    description:
      'Research claims are traced to the cited paper, review, or authoritative guidance.',
    illustration: '/images/editorial/researching.svg',
    illustrationAlt: 'A researcher comparing several sources',
  },
  {
    title: 'Verify current details',
    description:
      'Platform rules, prices, laws, and settings are checked against current official sources.',
    illustration: '/images/editorial/system-update.svg',
    illustrationAlt: 'A person updating information on a screen',
  },
  {
    title: 'Disclose ownership',
    description:
      'Comparisons disclose SinShield ownership and describe meaningful alternatives and limitations.',
    illustration: '/images/editorial/disclosure.svg',
    illustrationAlt: 'Two people reviewing a disclosure together',
  },
  {
    title: 'Handle corrections',
    description:
      'Corrections are reviewed through the process described in our editorial standards.',
    illustration: '/images/editorial/document-review.svg',
    illustrationAlt: 'A person carefully reviewing a document',
  },
]
</script>

<template>
  <main>
    <section id="hero" class="app-shell relative z-40 min-h-[72svh]">
      <BaseContainer
        class="flex min-h-[72svh] flex-col justify-center pb-20 pt-28 sm:pb-24 sm:pt-32 md:pb-28 md:pt-36 lg:pb-32 lg:pt-40"
      >
        <TheBreadCrumbs :items="breadcrumbs" color="#ffffff" class="mb-10 sm:mb-14" />

        <header class="grid items-center gap-10 md:grid-cols-[13rem_minmax(0,1fr)] lg:gap-16">
          <div
            class="grid aspect-square w-full max-w-52 place-items-center rounded-[2rem] bg-white p-5 shadow-2xl shadow-black/20"
          >
            <img
              :src="sinShieldEditorial.image"
              :alt="sinShieldEditorial.imageAlt"
              width="208"
              height="208"
              class="w-full rounded-3xl"
            />
          </div>

          <div>
            <h1
              class="max-w-4xl text-[clamp(2.5rem,9.5vw,3.5rem)] font-extrabold leading-[1.04] tracking-[-0.035em] text-white sm:text-[56px] lg:text-[4rem]"
            >
              {{ sinShieldEditorial.name }}
            </h1>
            <p class="mt-6 max-w-4xl text-[17px] leading-[1.6] text-white/85 sm:text-xl">
              SinShield Editorial is the sole author and reviewer behind the SinShield blog. The
              byline combines product knowledge with source-led research to publish practical
              guidance about adult-content protection, platform controls, digital habits, and the
              limits of blocking technology.
            </p>
          </div>
        </header>
      </BaseContainer>
    </section>

    <section class="about-footer-blend py-16 sm:py-20 lg:py-32">
      <BaseContainer>
        <article class="mx-auto max-w-4xl space-y-12 sm:space-y-16">
          <section>
            <h2
              class="font-display text-3xl font-extrabold tracking-[-0.03em] text-navy sm:text-4xl"
            >
              What this byline means
            </h2>
            <p class="mt-5 text-lg leading-8 text-navy/72">
              This organizational byline is used because every article is produced and maintained as
              part of the SinShield product publication, not presented as the independent opinion of
              a licensed clinician or lawyer. It also makes the commercial relationship clear:
              SinShield is our product, and recommendations involving it must disclose that fact.
            </p>
          </section>

          <section>
            <header class="text-center">
              <h2
                class="font-display text-3xl font-extrabold tracking-[-0.03em] text-navy sm:text-4xl"
              >
                How Every Article Is Reviewed
              </h2>
            </header>

            <div class="relative mt-14 sm:mt-16">
              <span
                aria-hidden="true"
                class="absolute bottom-6 left-5 top-6 w-px bg-navy/15 md:left-1/2 md:-translate-x-px"
              />

              <ol class="space-y-16 sm:space-y-20 md:space-y-10">
                <li
                  v-for="(step, index) in reviewSteps"
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

          <section class="pt-10 text-white sm:pt-16">
            <h2 class="font-display text-3xl font-extrabold tracking-[-0.03em] sm:text-4xl">
              Contact the author
            </h2>
            <p class="mt-5 text-lg leading-8 text-white/75">
              Send source suggestions, corrections, or questions to
              <a
                href="mailto:support@sinshield.app"
                class="font-semibold text-white underline underline-offset-4"
                >support@sinshield.app</a
              >. You can also read the complete
              <a
                href="/editorial-standards"
                class="font-semibold text-white underline underline-offset-4"
                >editorial standards</a
              >.
            </p>
          </section>
        </article>
      </BaseContainer>
    </section>
  </main>
</template>
