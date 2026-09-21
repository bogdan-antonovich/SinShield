<script setup lang="ts">
import BaseButton from '@/common/components/BaseButton.vue'
import BaseContainer from '@/common/components/BaseContainer.vue'
import InteractiveShowcaseSection from '@/common/components/InteractiveShowcaseSection.vue'
import { usePageMetadata } from '@/common/composables/usePageMetadata'
import TheBreadCrumbs from '@/layouts/components/TheBreadCrumbs.vue'

defineOptions({ name: 'AboutView' })

usePageMetadata({
  title: 'About SinShield | Private Adult-Content Protection',
  description:
    'Learn why SinShield exists, how its private protection works, what it can and cannot do, and how to contact the team.',
  path: '/about',
  image: '/sinshield-thumbnail.jpg',
})

interface Benefit {
  title: string
  description: string
  illustration: string
  illustrationAlt: string
  imageFit?: 'cover' | 'contain'
}

const breadcrumbs = [{ label: 'Home', to: '/' }, { label: 'About' }]
const benefits: Benefit[] = [
  {
    title: 'Private image analysis',
    description:
      'Visible frames in supported apps are analyzed and discarded on the phone instead of being uploaded to SinShield.',
    illustration: '/images/about/image-focus.svg',
    illustrationAlt: 'A person reviewing a framed image',
    imageFit: 'contain',
  },
  {
    title: 'Screens and websites',
    description:
      'Screen-level covering protects supported feeds, while local DNS filtering blocks requests to known adult domains.',
    illustration: '/images/about/mobile-encryption.svg',
    illustrationAlt: 'A person beside a protected mobile device',
    imageFit: 'contain',
  },
  {
    title: 'Less data by design',
    description:
      'You can use SinShield without creating a cloud profile, and it does not provide remote activity reports to another person.',
    illustration: '/images/about/no-data.svg',
    illustrationAlt: 'Two empty data cards representing minimal data collection',
    imageFit: 'contain',
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
            This Is SinShield
          </h1>
          <p
            class="max-w-236.75 text-center text-[17px] font-normal leading-[1.5] text-white/85 sm:text-xl md:text-2xl"
          >
            SinShield helps you leave porn behind by covering sexual content in supported apps and
            blocking known adult websites before they load. It works quietly in the background and
            keeps your activity private.
          </p>
          <BaseButton to="/products" variant="inverse" class="mt-10 w-full sm:w-fit">
            Get Started
          </BaseButton>
        </header>
      </BaseContainer>
    </section>

    <InteractiveShowcaseSection
      id="about-benefits"
      heading="Protection Built Around Privacy"
      description="SinShield adds practical protection without turning your private activity into a product. Its two layers work locally and require no cloud account."
      :items="benefits"
      tablist-label="SinShield benefits"
      theme="light"
    />

    <section class="about-footer-blend py-16 sm:py-20 lg:py-32">
      <BaseContainer>
        <div class="mx-auto max-w-4xl space-y-12 sm:space-y-16">
          <section>
            <h2
              class="font-display text-3xl font-extrabold tracking-[-0.03em] text-navy sm:text-4xl"
            >
              Why we built it
            </h2>
            <p class="mt-5 text-lg leading-8 text-navy/72">
              People usually do not need another lecture about a habit they already want to change.
              They need practical support at the point where a routine becomes automatic, without
              surrendering a sensitive browsing history or a stream of screenshots to a remote
              service.
            </p>
            <p class="mt-5 text-lg leading-8 text-navy/72">
              SinShield therefore focuses on interruption rather than surveillance. It can cover an
              unsafe result, reject a known adult-domain request, and create a short pause, but the
              person using the phone still decides what happens next.
            </p>
          </section>

          <section>
            <h2
              class="font-display text-3xl font-extrabold tracking-[-0.03em] text-navy sm:text-4xl"
            >
              What SinShield does not promise
            </h2>
            <p class="mt-5 text-lg leading-8 text-navy/72">
              SinShield is not therapy, a medical treatment, or a guarantee that every unsafe image
              or website will be blocked. Automated detection can make mistakes, device permissions
              can be revoked, and determined users can change their settings. The app is a practical
              protection layer, not a claim that technology can make a difficult choice disappear.
            </p>
            <BaseButton to="/products" class="mt-10 w-full"> Get Started </BaseButton>
          </section>

          <section class="pt-10 text-white sm:pt-16">
            <h2 class="font-display text-3xl font-extrabold tracking-[-0.03em] sm:text-4xl">
              How to reach us
            </h2>
            <p class="mt-5 text-lg leading-8 text-white/75">
              Questions, corrections, privacy concerns, and product feedback are welcome at
              <a
                href="mailto:support@sinshield.app"
                class="font-semibold text-white underline underline-offset-4"
                >support@sinshield.app</a
              >. For how our articles are researched and corrected, read our
              <a
                href="/editorial-standards"
                class="font-semibold text-white underline underline-offset-4"
                >editorial standards</a
              >.
            </p>
          </section>
        </div>
      </BaseContainer>
    </section>
  </main>
</template>
