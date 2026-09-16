<script setup lang="ts">
import BaseButton from '@/common/components/BaseButton.vue'
import BaseContainer from '@/common/components/BaseContainer.vue'
</script>

<template>
  <section id="what-that-is" class="overflow-hidden bg-surface py-20 sm:py-24 lg:py-32">
    <BaseContainer
      class="grid items-center gap-16 md:grid-cols-[minmax(280px,0.72fr)_minmax(0,1fr)] md:gap-12 lg:gap-24"
    >
      <div class="flex justify-center md:justify-start">
        <div
          class="relative aspect-[864/1821] w-[240px] overflow-hidden rounded-[2.275rem] sm:w-[290px] sm:rounded-[3.25rem] lg:w-[320px] lg:rounded-[3.5rem] drop-shadow-2xl"
        >
          <video
            src="/images/view-framed.mp4"
            class="pointer-events-none h-full w-full select-none object-cover"
            aria-label="SinShield detecting and covering unsafe content"
            autoplay
            disablepictureinpicture
            draggable="false"
            loop
            muted
            playsinline
            preload="metadata"
          ></video>
        </div>
      </div>

      <div class="max-w-2xl">
        <h2
          class="mb-6 font-display text-[36px] font-extrabold leading-[1.08] tracking-[-0.025em] text-navy sm:text-[48px] lg:text-[56px]"
        >
          Control Still Belongs to You
        </h2>

        <div class="space-y-5 text-lg font-normal leading-[1.55] text-navy/75 sm:text-xl">
          <p>
            SinShield does not make the decision for you. It creates a brief pause between seeing
            sexual content and acting on the impulse, giving you time to breathe and choose what
            happens next.
          </p>
          <p>
            When the shield appears, you can scroll past, close the app, or return to your feed. The
            choice remains yours; SinShield simply makes it easier to follow the decision you made
            with a clear head.
          </p>
        </div>

        <BaseButton to="/products" class="mt-10"> Get Started </BaseButton>
      </div>
    </BaseContainer>
  </section>
</template>
