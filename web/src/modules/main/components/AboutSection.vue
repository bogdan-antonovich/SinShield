<script setup lang="ts">
import BaseButton from '@/common/components/BaseButton.vue'
import BaseContainer from '@/common/components/BaseContainer.vue'
</script>

<template>
  <section id="about" class="overflow-hidden bg-surface py-16 sm:py-20 lg:py-32">
    <BaseContainer
      class="grid items-center gap-12 lg:grid-cols-[minmax(0,1fr)_minmax(280px,0.72fr)] lg:gap-20 xl:gap-24"
    >
      <div class="order-1 max-w-2xl">
        <h2
          class="mb-5 font-display text-[34px] font-extrabold leading-[1.08] tracking-[-0.025em] text-navy sm:mb-6 sm:text-[44px] lg:text-[56px]"
        >
          Protection You Can Rely On
        </h2>

        <div class="space-y-4 text-base font-normal leading-[1.65] text-navy/75 sm:space-y-5 sm:text-lg lg:text-xl">
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

        <BaseButton to="/products" class="mt-10 w-full sm:w-fit">
          Get Started
        </BaseButton>
      </div>

      <div class="order-2 flex justify-center lg:justify-end">
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
