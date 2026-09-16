<script setup lang="ts">
import BaseButton from '@/common/components/BaseButton.vue'
import BaseContainer from '@/common/components/BaseContainer.vue'
</script>

<template>
  <section id="advanced_tuning" class="overflow-hidden bg-surface py-20 sm:py-24 lg:py-32">
    <BaseContainer
      class="grid items-center gap-16 md:grid-cols-[minmax(0,1fr)_minmax(280px,0.72fr)] md:gap-12 lg:gap-24"
    >
      <div class="max-w-2xl">
        <h2
          class="mb-6 font-display text-[36px] font-extrabold leading-[1.08] tracking-[-0.025em] text-navy sm:text-[48px] lg:text-[56px]"
        >
          You Decide How Strict Protection Should Be
        </h2>

        <div class="space-y-5 text-lg font-normal leading-[1.55] text-navy/75 sm:text-xl">
          <p>
            SinShield works well with its recommended settings, but its detection sensitivity
            controls let you decide how cautious protection should be by adjusting when explicit or
            suggestive content is covered and when an uncertain result should receive a second check
            before the app decides what to do.
          </p>
          <p>
            Lower settings help SinShield catch more questionable content, while higher settings
            reduce the chance of ordinary posts being covered, which means you can choose stronger
            protection or a lighter touch and change that balance whenever your needs change.
          </p>
        </div>

        <BaseButton to="/products" class="mt-10"> Get Started </BaseButton>
      </div>

      <div class="flex justify-center md:justify-end">
        <img
          src="/images/AdvancedSettings.png"
          alt="SinShield detection sensitivity controls on a Google Pixel 10 Pro"
          class="aspect-[1410/2968] w-[250px] object-contain drop-shadow-2xl sm:w-[290px] lg:w-[320px]"
          decoding="async"
        />
      </div>
    </BaseContainer>
  </section>
</template>
