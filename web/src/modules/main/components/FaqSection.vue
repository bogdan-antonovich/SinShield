<script setup lang="ts">
import { computed } from 'vue'

import BaseContainer from '@/common/components/BaseContainer.vue'

interface FaqItem {
  question: string
  answer: string
}

const defaultFaqItems: FaqItem[] = [
  {
    question: 'What does SinShield block?',
    answer:
      'SinShield covers explicit and suggestive content inside supported apps, including Instagram and X, and blocks known adult websites through a separate website-protection layer. You can also add domains to your personal block list.',
  },
  {
    question: 'Are my screenshots or browsing activity uploaded?',
    answer:
      'No. Screen captures are analyzed by models running on your Android device and are not sent to SinShield. Website protection checks standard DNS requests locally; SinShield does not receive your browsing history or inspect the contents of the pages you visit.',
  },
  {
    question: 'Why does SinShield need Accessibility and overlay permissions?',
    answer:
      'Android requires Accessibility permission so SinShield can notice changes in supported apps and capture the visible screen for on-device analysis. The overlay permission lets it place a cover over unsafe content. You grant and can revoke both permissions in Android settings.',
  },
  {
    question: 'Is website protection a regular VPN?',
    answer:
      'No. SinShield uses Android’s local VPN feature only to filter standard DNS requests for known adult domains. It does not route, decrypt, or inspect your web traffic. Because Android allows one VPN at a time, website protection cannot run alongside another VPN.',
  },
  {
    question: 'Can SinShield make a mistake?',
    answer:
      'Like any automated detection, it can occasionally cover safe content or miss something unsafe. You can report a false positive from the block screen, and SinShield remembers that correction locally so the same frame is not covered again.',
  },
  {
    question: 'Can I choose how strict the protection is?',
    answer:
      'Yes. SinShield includes five protection levels, from Relaxed to Maximum, a separate option for suggestive content, and advanced detection controls. The recommended settings are a good place to start.',
  },
  {
    question: 'What happens when content is blocked?',
    answer:
      'SinShield creates a short pause and covers the unsafe content. You can then move past it, return to your feed, or close the app. The shield is there to interrupt the impulse, while the final choice stays with you.',
  },
  {
    question: 'Do I need an account or subscription?',
    answer:
      'No. SinShield is free to use and does not require an account or cloud profile. It is currently available for Android 11 and newer.',
  },
]

const props = defineProps<{
  title?: string
  items?: readonly FaqItem[]
  sectionId?: string
}>()

const title = computed(() => props.title ?? 'FAQ')
const items = computed(() => props.items ?? defaultFaqItems)
const sectionId = computed(() => props.sectionId ?? 'faq')
</script>

<template>
  <section
    :id="sectionId"
    class="bg-surface py-16 sm:py-20 lg:py-32"
    :aria-labelledby="`${sectionId}-heading`"
  >
    <BaseContainer>
      <h2
        :id="`${sectionId}-heading`"
        class="text-center font-display text-[34px] font-extrabold leading-[1.08] tracking-[-0.025em] text-navy sm:text-[44px] lg:text-[56px]"
      >
        {{ title }}
      </h2>

      <div class="mx-auto mt-12 max-w-4xl border-t border-navy/15 sm:mt-14">
        <details
          v-for="item in items"
          :key="item.question"
          class="group border-b border-navy/15"
        >
          <summary
            class="flex min-h-14 cursor-pointer list-none items-center justify-between gap-4 py-5 font-display text-lg font-bold leading-tight text-navy marker:hidden sm:gap-6 sm:py-7 sm:text-2xl [&::-webkit-details-marker]:hidden"
          >
            <span>{{ item.question }}</span>
            <span
              aria-hidden="true"
              class="relative size-5 shrink-0 text-primary before:absolute before:left-0 before:top-1/2 before:h-0.5 before:w-5 before:-translate-y-1/2 before:rounded-full before:bg-current after:absolute after:left-1/2 after:top-0 after:h-5 after:w-0.5 after:-translate-x-1/2 after:rounded-full after:bg-current after:transition-transform after:duration-200 group-open:after:rotate-90"
            ></span>
          </summary>
          <p class="max-w-3xl pb-7 pr-10 text-base leading-7 text-navy/70 sm:text-lg sm:leading-8">
            {{ item.answer }}
          </p>
        </details>
      </div>
    </BaseContainer>
  </section>
</template>
