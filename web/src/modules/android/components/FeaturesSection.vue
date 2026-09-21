<script setup lang="ts">
import BaseContainer from '@/common/components/BaseContainer.vue'

type FeatureIcon = 'privacy' | 'apps' | 'layers' | 'detection' | 'levels' | 'account' | 'website'

interface Feature {
  label: string
  value: string
  detail: string
  icon: FeatureIcon
  featured?: boolean
}

const features: Feature[] = [
  {
    label: 'On-device privacy',
    value: '0 screenshots uploaded',
    detail: 'Visible frames are analyzed and discarded on your phone.',
    icon: 'privacy',
    featured: true,
  },
  {
    label: 'Supported apps',
    value: 'Instagram + X',
    detail: 'Focused protection for the apps SinShield supports today.',
    icon: 'apps',
  },
  {
    label: 'Protection layers',
    value: '2 layers',
    detail: 'Screen shielding and local DNS filtering work independently.',
    icon: 'layers',
  },
  {
    label: 'Detection',
    value: '2-model checks',
    detail: 'Borderline results can receive an independent second opinion.',
    icon: 'detection',
  },
  {
    label: 'Protection levels',
    value: '5 levels',
    detail: 'Choose from Relaxed through Maximum sensitivity.',
    icon: 'levels',
  },
  {
    label: 'Account required',
    value: 'None',
    detail: 'Start protecting your device without creating a cloud profile.',
    icon: 'account',
  },
  {
    label: 'Website control',
    value: 'Built-in + custom',
    detail: 'Block known adult sites and add domains of your own.',
    icon: 'website',
  },
]
</script>

<template>
  <section id="features" class="overflow-hidden bg-surface py-16 sm:py-20 lg:py-32">
    <BaseContainer>
      <div class="mx-auto max-w-4xl text-center">
        <h2
          class="font-display text-[34px] font-extrabold leading-[1.08] tracking-[-0.025em] text-navy sm:text-[44px] lg:text-[56px]"
        >
          What SinShield Does for You
        </h2>

        <p
          class="mx-auto mt-6 max-w-3xl text-lg font-normal leading-[1.55] text-navy/75 sm:text-xl"
        >
          SinShield gives you practical protection in the places where adult content is easiest to
          reach. It covers unsafe content in supported apps, blocks known adult websites, keeps
          every check on your phone, and lets you decide how strict protection should be.
        </p>
      </div>

      <div
        class="mt-12 grid gap-px overflow-hidden rounded-[1.5rem] border border-navy/10 bg-navy/10 sm:mt-14 sm:grid-cols-2 sm:rounded-[1.75rem] lg:mt-20 lg:grid-cols-4"
      >
        <article
          v-for="feature in features"
          :key="feature.label"
          class="flex min-h-56 flex-col bg-white p-6 sm:min-h-64 sm:p-8 lg:min-h-72"
          :class="feature.featured ? 'sm:col-span-2 lg:col-span-2' : ''"
        >
          <div class="flex items-center gap-3 text-primary">
            <svg
              v-if="feature.icon === 'privacy'"
              viewBox="0 0 24 24"
              fill="none"
              class="size-6"
              aria-hidden="true"
            >
              <path
                d="M12 3.25 19 6v5.25c0 4.35-2.67 7.67-7 9.5-4.33-1.83-7-5.15-7-9.5V6l7-2.75Z"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <path
                d="m8.9 12.1 2.05 2.05 4.4-4.55"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>

            <svg
              v-else-if="feature.icon === 'apps'"
              viewBox="0 0 24 24"
              fill="none"
              class="size-6"
              aria-hidden="true"
            >
              <rect
                x="5"
                y="2.75"
                width="14"
                height="18.5"
                rx="3"
                stroke="currentColor"
                stroke-width="1.7"
              />
              <path
                d="M9.5 6.25h5M10.5 17.5h3"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linecap="round"
              />
            </svg>

            <svg
              v-else-if="feature.icon === 'layers'"
              viewBox="0 0 24 24"
              fill="none"
              class="size-6"
              aria-hidden="true"
            >
              <path
                d="m12 3 9 5-9 5-9-5 9-5Z"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linejoin="round"
              />
              <path
                d="m4.5 12 7.5 4.2 7.5-4.2M4.5 16l7.5 4.2 7.5-4.2"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>

            <svg
              v-else-if="feature.icon === 'detection'"
              viewBox="0 0 24 24"
              fill="none"
              class="size-6"
              aria-hidden="true"
            >
              <path
                d="M8 3H5a2 2 0 0 0-2 2v3M16 3h3a2 2 0 0 1 2 2v3M8 21H5a2 2 0 0 1-2-2v-3M16 21h3a2 2 0 0 0 2-2v-3"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linecap="round"
              />
              <circle cx="12" cy="12" r="3.25" stroke="currentColor" stroke-width="1.7" />
            </svg>

            <svg
              v-else-if="feature.icon === 'levels'"
              viewBox="0 0 24 24"
              fill="none"
              class="size-6"
              aria-hidden="true"
            >
              <path
                d="M4 6h6M14 6h6M4 12h10M18 12h2M4 18h2M10 18h10"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linecap="round"
              />
              <circle cx="12" cy="6" r="2" stroke="currentColor" stroke-width="1.7" />
              <circle cx="16" cy="12" r="2" stroke="currentColor" stroke-width="1.7" />
              <circle cx="8" cy="18" r="2" stroke="currentColor" stroke-width="1.7" />
            </svg>

            <svg
              v-else-if="feature.icon === 'account'"
              viewBox="0 0 24 24"
              fill="none"
              class="size-6"
              aria-hidden="true"
            >
              <circle cx="12" cy="8" r="3.5" stroke="currentColor" stroke-width="1.7" />
              <path
                d="M5.5 20c.55-3.5 2.72-5.25 6.5-5.25S17.95 16.5 18.5 20"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linecap="round"
              />
              <path
                d="m18 5 3 3m0-3-3 3"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linecap="round"
              />
            </svg>

            <svg v-else viewBox="0 0 24 24" fill="none" class="size-6" aria-hidden="true">
              <circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.7" />
              <path
                d="M3.5 12h17M12 3c2.2 2.4 3.3 5.4 3.3 9S14.2 18.6 12 21c-2.2-2.4-3.3-5.4-3.3-9S9.8 5.4 12 3Z"
                stroke="currentColor"
                stroke-width="1.7"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>

            <span class="text-[15px] font-medium leading-tight sm:text-base">{{
              feature.label
            }}</span>
          </div>

          <div class="mt-auto pt-9 sm:pt-12">
            <p
              class="font-display font-medium leading-[1.05] tracking-[-0.035em] text-navy"
              :class="
                feature.featured
                  ? 'text-[38px] sm:text-[48px] lg:text-[54px]'
                  : 'text-[32px] sm:text-[36px]'
              "
            >
              {{ feature.value }}
            </p>
            <p class="mt-4 max-w-md text-[15px] leading-[1.5] text-navy/70 sm:text-base">
              {{ feature.detail }}
            </p>
          </div>
        </article>
      </div>
    </BaseContainer>
  </section>
</template>
