<script setup lang="ts">
import BaseButton from '@/common/components/BaseButton.vue'
import BaseContainer from '@/common/components/BaseContainer.vue'

const GOOGLE_PLAY_URL = 'https://play.google.com/store/apps/details?id=com.example.sinshield'

const androidSteps = [
  { label: 'Click the above download button' },
  { label: 'Install the Android app' },
  { label: 'Stay protected' },
]
</script>

<template>
  <section id="steps" class="overflow-hidden bg-surface py-20 sm:py-24 lg:py-32">
    <BaseContainer>
      <div class="mx-auto max-w-4xl text-center">
        <h2
          class="mb-[25px] font-display text-[25px] font-extrabold leading-[1.08] tracking-[-0.025em] text-navy sm:text-[48px] lg:text-[56px]"
        >
          <span>Sin</span><span class="text-primary">Shield.</span> for Android
        </h2>

        <BaseButton
          :href="GOOGLE_PLAY_URL"
          variant="inverse"
          class="gap-3"
          aria-label="Get it on Google Play"
        >
          <span>Get it on</span>
          <img src="/icons/google-play.svg" alt="" aria-hidden="true" class="h-6 w-auto" />
        </BaseButton>
      </div>

      <ol class="mx-auto mt-14 flex max-w-lg flex-col gap-6 lg:mt-20">
        <li
          v-for="(step, index) in androidSteps"
          :key="step.label"
          class="flex items-center gap-5"
        >
          <span
            class="flex size-12 shrink-0 items-center justify-center rounded-full bg-primary font-display text-xl font-bold text-white"
            aria-hidden="true"
          >
            {{ index + 1 }}
          </span>
          <p class="text-left text-lg font-semibold leading-snug text-navy">
            {{ step.label }}
          </p>
        </li>
      </ol>
    </BaseContainer>
  </section>
</template>
