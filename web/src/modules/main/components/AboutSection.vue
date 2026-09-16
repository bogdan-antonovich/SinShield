<script setup lang="ts">
import BaseButton from '@/common/components/BaseButton.vue'
import BaseContainer from '@/common/components/BaseContainer.vue'
</script>

<template>
  <section id="about" class="overflow-hidden bg-surface py-20 sm:py-24 lg:py-32">
    <BaseContainer
      class="grid items-center gap-16 md:grid-cols-[minmax(0,1fr)_minmax(280px,0.72fr)] md:gap-12 lg:gap-24"
    >
      <div class="max-w-2xl">
        <h2
          class="mb-6 font-display text-[36px] font-extrabold leading-[1.08] tracking-[-0.025em] text-navy sm:text-[48px] lg:text-[56px]"
        >
          Protection You Can Rely On
        </h2>

        <div class="space-y-5 text-lg font-normal leading-[1.55] text-navy/75 sm:text-xl">
          <p>
            Leaving porn behind is not a question of understanding the damage, because most men here
            already know exactly what it costs them. The value of this app is practical: it makes
            access less automatic, giving a decision made with a clear head more weight than an
            impulse that lasts a few minutes.
          </p>
          <p>
            The app keeps porn and other sexual content off your screen, while everything happens on
            your phone, so your activity is not sent anywhere. It runs in the background and steps
            in when adult content appears.
          </p>
        </div>

        <BaseButton to="/products" class="mt-10"> Get Started </BaseButton>
      </div>

      <div class="flex justify-center md:justify-end">
        <img
          src="/images/SinShieldPreview.png"
          alt="SinShield protection status and permissions on a Google Pixel 10 Pro"
          class="aspect-[1410/2968] w-[250px] object-contain drop-shadow-2xl sm:w-[290px] lg:w-[320px]"
          decoding="async"
        />
      </div>
    </BaseContainer>
  </section>
</template>
