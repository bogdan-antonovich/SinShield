<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import BaseButton from '@/common/components/BaseButton.vue'
import BaseContainer from '@/common/components/BaseContainer.vue'
import { usePageMetadata } from '@/common/composables/usePageMetadata'

defineOptions({ name: 'NotFoundView' })

const route = useRoute()
const pagePath = computed(() => route.path)

usePageMetadata(() => ({
  title: 'Page Not Found | SinShield',
  description:
    'The page you were looking for could not be found. Return to SinShield or explore protection for Android.',
  path: pagePath.value,
  robots: 'noindex, follow',
}))
</script>

<template>
  <main>
    <section class="min-h-[100svh] bg-white">
      <BaseContainer
        class="flex min-h-[100svh] flex-col items-center justify-center pb-20 pt-28 text-center sm:pb-24 sm:pt-32 md:pb-28 md:pt-36 lg:pb-32 lg:pt-40"
      >
        <p
          class="font-display text-[clamp(6rem,22vw,12rem)] font-extrabold leading-[0.8] tracking-[-0.06em] text-primary"
          aria-hidden="true"
        >
          404
        </p>

        <h1
          id="not-found-heading"
          class="mt-9 max-w-3xl font-display text-[clamp(2.1rem,7vw,4.75rem)] font-extrabold leading-[1.02] tracking-[-0.045em] text-navy"
        >
          Looks like this page slipped through.
        </h1>
        <p class="mt-6 max-w-2xl text-lg leading-8 text-navy/68 sm:text-xl sm:leading-9">
          We couldn’t find the page you were looking for.
        </p>

        <div
          class="mt-10 flex w-full max-w-xl flex-col items-stretch justify-center gap-4 sm:flex-row sm:items-center"
        >
          <BaseButton to="/" class="w-full sm:w-auto">Back to homepage</BaseButton>
          <RouterLink
            to="/products"
            class="inline-flex min-h-13 items-center justify-center rounded-full px-7 font-display text-[15px] font-bold text-navy ring-1 ring-inset ring-navy/15 transition hover:bg-surface hover:text-primary focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-primary/30 sm:min-h-14 sm:px-8 sm:text-base"
          >
            View products
          </RouterLink>
        </div>
      </BaseContainer>
    </section>
  </main>
</template>
