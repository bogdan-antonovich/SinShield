<script setup lang="ts">
import BaseButton from '@/common/components/BaseButton.vue'
import BaseContainer from '@/common/components/BaseContainer.vue'
</script>

<template>
  <section id="what-that-is" class="overflow-hidden bg-surface py-16 sm:py-20 lg:py-32">
    <BaseContainer
      class="grid items-center gap-12 lg:grid-cols-[minmax(280px,0.72fr)_minmax(0,1fr)] lg:gap-20 xl:gap-24"
    >
      <div class="order-2 flex justify-center lg:order-1 lg:justify-start">
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

      <div class="order-1 max-w-2xl lg:order-2">
        <h2
          class="mb-5 font-display text-[34px] font-extrabold leading-[1.08] tracking-[-0.025em] text-navy sm:mb-6 sm:text-[44px] lg:text-[56px]"
        >
          Control Still Belongs to You
        </h2>

        <div class="space-y-4 text-base font-normal leading-[1.65] text-navy/75 sm:space-y-5 sm:text-lg lg:text-xl">
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
