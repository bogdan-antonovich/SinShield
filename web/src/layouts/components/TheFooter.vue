<script setup lang="ts">
import BaseContainer from '@/common/components/BaseContainer.vue'

const resourceLinks = [
  { label: 'About SinShield', href: '/about' },
  { label: 'Editorial standards', href: '/editorial-standards' },
  { label: 'SinShield Editorial', href: '/authors/sinshield-editorial' },
  { label: 'How it works', href: '/#how-it-works' },
  { label: 'Protection', href: '/#about_protection' },
  { label: 'FAQ', href: '/#faq' },
]

const legalLinks = [
  { label: 'Privacy Policy', href: '/privacy-policy' },
  { label: 'Terms & Conditions', href: '/terms-and-conditions' },
]

const currentYear = new Date().getFullYear()
</script>

<template>
  <footer class="app-shell text-white" aria-labelledby="footer-brand">
    <BaseContainer class="py-14 sm:py-18 lg:py-24">
      <div
        class="grid gap-x-10 gap-y-12 border-b border-white/15 pb-14 sm:grid-cols-2 sm:pb-16 lg:grid-cols-[1.55fr_0.75fr_0.75fr_0.9fr] lg:gap-12"
      >
        <div class="max-w-md sm:col-span-2 lg:col-span-1">
          <a
            href="/"
            class="group inline-flex items-center gap-4 rounded-2xl focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-white/40"
            aria-label="SinShield home"
          >
            <span
              class="grid size-14 shrink-0 place-items-center overflow-hidden rounded-2xl bg-white shadow-lg shadow-black/15 sm:size-16"
            >
              <img
                src="/sinshield-thumbnail.png"
                alt=""
                class="size-full object-cover"
                aria-hidden="true"
              />
            </span>
            <span
              id="footer-brand"
              class="font-display text-3xl font-extrabold tracking-[-0.04em] sm:text-[34px]"
            >
              Sin<span class="text-[#49adff]">Shield.</span>
            </span>
          </a>

          <p class="mt-6 text-base leading-7 text-white/75 sm:text-[17px]">
            SinShield is a free adult content blocker that works directly on your device. It keeps
            porn and other sexual content off your screen without sending your activity anywhere.
          </p>
        </div>

        <div>
          <h2 class="font-display text-xs font-bold uppercase tracking-[0.18em] text-white/45">
            Products
          </h2>
          <ul class="mt-6 space-y-4">
            <li>
              <a
                href="/products/android"
                class="break-all rounded-md font-display text-[15px] font-semibold text-white/70 underline-offset-4 transition hover:text-white hover:underline focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-white/40"
              >
                For Android
              </a>
            </li>
          </ul>
        </div>

        <div>
          <h2 class="font-display text-xs font-bold uppercase tracking-[0.18em] text-white/45">
            Resources
          </h2>
          <ul class="mt-6 space-y-4">
            <li v-for="link in resourceLinks" :key="link.label">
              <a
                :href="link.href"
                class="rounded-md font-display text-[15px] font-semibold text-white/70 underline-offset-4 transition hover:text-white hover:underline focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-white/40"
              >
                {{ link.label }}
              </a>
            </li>
          </ul>
        </div>

        <div>
          <h2 class="font-display text-xs font-bold uppercase tracking-[0.18em] text-white/45">
            Support & legal
          </h2>
          <ul class="mt-6 space-y-4">
            <li>
              <a
                href="mailto:support@sinshield.app"
                class="rounded-md font-display text-[15px] font-semibold text-white/70 underline-offset-4 transition hover:text-white hover:underline focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-white/40"
              >
                support@sinshield.app
              </a>
            </li>
            <li v-for="link in legalLinks" :key="link.label">
              <a
                :href="link.href"
                class="rounded-md font-display text-[15px] font-semibold text-white/70 underline-offset-4 transition hover:text-white hover:underline focus-visible:outline-none focus-visible:ring-3 focus-visible:ring-white/40"
              >
                {{ link.label }}
              </a>
            </li>
          </ul>
        </div>
      </div>

      <div class="pt-7 text-sm text-white/45">
        <p>&copy; {{ currentYear }} SinShield. All rights reserved.</p>
      </div>
    </BaseContainer>
  </footer>
</template>
