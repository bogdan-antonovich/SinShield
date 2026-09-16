<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

import BaseButton from '@/common/components/BaseButton.vue'
import BaseContainer from '@/common/components/BaseContainer.vue'

interface NavChild {
  label: string
  to: string
  description?: string
  icon?: string // optional emoji / short glyph shown in a tile, e.g. '🛡️'
}

interface NavItem {
  label: string
  to?: string
  children?: NavChild[]
}

const brand = { to: '/', lead: 'Sin', accent: 'Shield' }

const navItems: NavItem[] = [
  {
    label: 'Products',
    children: [
      {
        icon: '/icons/android-robot.svg',
        label: 'SinShield for Android',
        to: '/products/android',
        description: 'Available for Android 11+',
      },
    ],
  },
  { label: 'Blog', to: '/blog' },
  // { label: 'About', to: '#about' },
  // { label: 'Premium', to: '/premium' },
]

const languages = ['EN', 'RU', 'ES', 'DE']
const currentLang = ref(languages[0])

const cta = { label: 'Get started', to: '/products' }

// const LANG_KEY = '__lang__'
const openKey = ref<string | null>(null)
const mobileOpen = ref(false)
const isHeaderVisible = ref(true)
const root = ref<HTMLElement | null>(null)

const HEADER_HIDE_OFFSET = 160
let lastScrollY = 0
let scrollFrame: number | null = null

const open = (key: string) => (openKey.value = key)
const toggle = (key: string) => (openKey.value = openKey.value === key ? null : key)
const closeMenus = () => (openKey.value = null)

function selectLang(lang: string) {
  currentLang.value = lang
  closeMenus()
}

/* Close on outside click + Escape. */
function onDocClick(e: MouseEvent) {
  if (root.value && !root.value.contains(e.target as Node)) closeMenus()
}
function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    closeMenus()
    mobileOpen.value = false
  }
}

function updateHeaderVisibility() {
  const currentScrollY = Math.max(window.scrollY, 0)
  const scrollingDown = currentScrollY > lastScrollY
  const scrollingUp = currentScrollY < lastScrollY

  if (currentScrollY <= HEADER_HIDE_OFFSET || scrollingUp || mobileOpen.value) {
    isHeaderVisible.value = true
  } else if (scrollingDown) {
    isHeaderVisible.value = false
    closeMenus()
  }

  lastScrollY = currentScrollY
  scrollFrame = null
}

function onScroll() {
  if (scrollFrame === null) {
    scrollFrame = window.requestAnimationFrame(updateHeaderVisibility)
  }
}

onMounted(() => {
  lastScrollY = Math.max(window.scrollY, 0)
  document.addEventListener('click', onDocClick)
  document.addEventListener('keydown', onKeydown)
  window.addEventListener('scroll', onScroll, { passive: true })
})
onBeforeUnmount(() => {
  document.removeEventListener('click', onDocClick)
  document.removeEventListener('keydown', onKeydown)
  window.removeEventListener('scroll', onScroll)
  if (scrollFrame !== null) window.cancelAnimationFrame(scrollFrame)
  document.body.style.overflow = ''
})

/* Lock background scroll while the mobile sidebar is open. */
watch(mobileOpen, (open) => {
  document.body.style.overflow = open ? 'hidden' : ''
  if (open) isHeaderVisible.value = true
})

/* Close everything when the route changes. */
const route = useRoute()
watch(
  () => route.fullPath,
  () => {
    closeMenus()
    mobileOpen.value = false
    isHeaderVisible.value = true
  },
)
</script>

<template>
  <div
    ref="root"
    class="fixed left-36 right-0 top-0 z-50 transition-transform duration-300 ease-out motion-reduce:transition-none sm:left-44 md:left-0"
    :class="isHeaderVisible ? 'translate-y-0' : '-translate-y-full'"
    @focusin="isHeaderVisible = true"
  >
    <BaseContainer class="pt-4">
      <div
        class="flex items-center justify-between gap-4 rounded-full bg-surface px-5 py-3 shadow-xl shadow-primary/10 ring-1 ring-navy/5 backdrop-blur"
      >
        <!-- Brand -->
        <RouterLink
          :to="brand.to"
          class="font-display text-2xl font-extrabold tracking-tight"
          aria-label="Home"
        >
          <span class="text-navy">{{ brand.lead }}</span
          ><span class="text-primary">{{ brand.accent }}.</span>
        </RouterLink>

        <!-- Desktop nav -->
        <nav class="hidden items-center gap-1 md:flex">
          <template v-for="item in navItems" :key="item.label">
            <!-- Dropdown item -->
            <div
              v-if="item.children"
              class="relative"
              @mouseenter="open(item.label)"
              @mouseleave="closeMenus()"
            >
              <button
                type="button"
                class="flex items-center gap-1 rounded-full px-4 py-2 font-display text-[15px] font-semibold text-navy transition hover:text-primary"
                :aria-expanded="openKey === item.label"
                aria-haspopup="menu"
                @click="toggle(item.label)"
              >
                {{ item.label }}
                <svg
                  class="size-4 transition-transform duration-200"
                  :class="openKey === item.label ? 'rotate-180' : ''"
                  viewBox="0 0 20 20"
                  fill="none"
                  aria-hidden="true"
                >
                  <path
                    d="M5.5 7.5 10 12l4.5-4.5"
                    stroke="currentColor"
                    stroke-width="1.75"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
              </button>

              <Transition name="menu">
                <!-- pt-2 (not mt-2) keeps the gap inside the hoverable area so the
                   menu doesn't close when the cursor travels down to an item. -->
                <div v-if="openKey === item.label" class="absolute left-0 top-full pt-2">
                  <div
                    class="w-64 rounded-2xl bg-surface p-2 shadow-xl shadow-navy/10 ring-1 ring-navy/5"
                    role="menu"
                  >
                    <RouterLink
                      v-for="child in item.children"
                      :key="child.to"
                      :to="child.to"
                      role="menuitem"
                      class="flex items-center gap-3 rounded-xl px-3 py-2.5 transition hover:bg-primary/5"
                    >
                      <img
                        v-if="child.icon"
                        :src="child.icon"
                        alt=""
                        class="h-7 w-auto shrink-0"
                        aria-hidden="true"
                      />
                      <span>
                        <span class="block font-display text-sm font-semibold text-navy">{{
                          child.label
                        }}</span>
                        <span v-if="child.description" class="block text-xs text-navy/60">{{
                          child.description
                        }}</span>
                      </span>
                    </RouterLink>
                  </div>
                </div>
              </Transition>
            </div>

            <!-- Plain link -->
            <RouterLink
              v-else
              :to="item.to!"
              class="rounded-full px-4 py-2 font-display text-[15px] font-semibold text-navy transition hover:text-primary"
              active-class="text-primary"
            >
              {{ item.label }}
            </RouterLink>
          </template>
        </nav>

        <div class="hidden items-center gap-2 md:flex">
          <!-- <div class="relative" @mouseenter="open(LANG_KEY)" @mouseleave="closeMenus()">
          <button
            type="button"
            class="flex items-center gap-1.5 rounded-full px-3 py-2 font-display text-sm font-semibold text-navy transition hover:text-primary"
            :aria-expanded="openKey === LANG_KEY"
            aria-haspopup="menu"
            @click="toggle(LANG_KEY)"
          >
            <svg class="size-4.5" viewBox="0 0 20 20" fill="none" aria-hidden="true">
              <circle cx="10" cy="10" r="7.25" stroke="currentColor" stroke-width="1.5" />
              <path
                d="M2.75 10h14.5M10 2.75c2.2 2.4 2.2 12.1 0 14.5M10 2.75c-2.2 2.4-2.2 12.1 0 14.5"
                stroke="currentColor"
                stroke-width="1.5"
              />
            </svg>
            {{ currentLang }}
            <svg
              class="size-4 transition-transform duration-200"
              :class="openKey === LANG_KEY ? 'rotate-180' : ''"
              viewBox="0 0 20 20"
              fill="none"
              aria-hidden="true"
            >
              <path
                d="M5.5 7.5 10 12l4.5-4.5"
                stroke="currentColor"
                stroke-width="1.75"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
            </svg>
          </button>

          <Transition name="menu">
            <div v-if="openKey === LANG_KEY" class="absolute right-0 top-full pt-2">
              <div
                class="w-28 rounded-2xl bg-surface p-1.5 shadow-xl shadow-navy/10 ring-1 ring-navy/5"
                role="menu"
              >
                <button
                  v-for="lang in languages"
                  :key="lang"
                  type="button"
                  role="menuitem"
                  class="flex w-full items-center justify-between rounded-lg px-3 py-2 text-sm font-semibold text-navy transition hover:bg-primary/5"
                  @click="selectLang(lang)"
                >
                  {{ lang }}
                  <span v-if="lang === currentLang" class="text-primary" aria-hidden="true">●</span>
                </button>
              </div>
            </div>
          </Transition>
        </div> -->

          <!-- CTA -->
          <BaseButton :to="cta.to" size="compact" class="ring-1 ring-inset ring-white/15">
            {{ cta.label }}
          </BaseButton>
        </div>

        <!-- Mobile toggle -->
        <button
          type="button"
          class="inline-flex size-10 items-center justify-center rounded-full text-navy transition hover:bg-primary/5 md:hidden"
          :aria-expanded="mobileOpen"
          aria-controls="mobile-menu"
          aria-label="Toggle menu"
          @click="mobileOpen = !mobileOpen"
        >
          <svg v-if="!mobileOpen" class="size-6" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path
              d="M4 7h16M4 12h16M4 17h16"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
            />
          </svg>
          <svg v-else class="size-6" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path
              d="M6 6l12 12M18 6 6 18"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
            />
          </svg>
        </button>
      </div>
    </BaseContainer>

    <!-- Mobile sidebar: dimmed backdrop -->
    <Transition name="fade">
      <div
        v-if="mobileOpen"
        class="fixed inset-0 z-40 bg-navy/40 backdrop-blur-sm md:hidden"
        @click="mobileOpen = false"
      />
    </Transition>

    <!-- Mobile sidebar: off-canvas drawer -->
    <Transition name="drawer">
      <nav
        v-if="mobileOpen"
        id="mobile-menu"
        class="fixed inset-y-0 right-0 z-50 flex h-dvh w-80 max-w-[85%] flex-col overflow-y-auto bg-surface p-5 shadow-2xl shadow-navy/20 md:hidden"
      >
        <!-- Drawer header: brand + close -->
        <div class="mb-2 flex items-center justify-between">
          <RouterLink
            :to="brand.to"
            class="font-display text-xl font-extrabold tracking-tight"
            aria-label="Home"
          >
            <span class="text-navy">{{ brand.lead }}</span
            ><span class="text-primary">{{ brand.accent }}.</span>
          </RouterLink>
          <button
            type="button"
            class="inline-flex size-9 items-center justify-center rounded-full text-navy transition hover:bg-primary/5"
            aria-label="Close menu"
            @click="mobileOpen = false"
          >
            <svg class="size-5" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path
                d="M6 6l12 12M18 6 6 18"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
              />
            </svg>
          </button>
        </div>

        <template v-for="item in navItems" :key="item.label">
          <RouterLink
            v-if="!item.children"
            :to="item.to!"
            class="block rounded-xl px-4 py-3 font-display font-semibold text-navy transition hover:bg-primary/5"
            active-class="text-primary"
          >
            {{ item.label }}
          </RouterLink>
          <div v-else class="py-1">
            <p class="px-4 pb-1 pt-2 text-xs font-semibold uppercase tracking-wide text-navy/50">
              {{ item.label }}
            </p>
            <RouterLink
              v-for="child in item.children"
              :key="child.to"
              :to="child.to"
              class="flex items-center gap-3 rounded-xl px-4 py-2.5 font-display text-sm font-semibold text-navy transition hover:bg-primary/5"
            >
              <img
                v-if="child.icon"
                :src="child.icon"
                alt=""
                class="h-6 w-auto shrink-0"
                aria-hidden="true"
              />
              {{ child.label }}
            </RouterLink>
          </div>
        </template>

        <div class="mt-auto flex flex-wrap gap-1 border-t border-navy/10 px-1 pt-3">
          <button
            v-for="lang in languages"
            :key="lang"
            type="button"
            class="rounded-full px-3 py-1.5 text-sm font-semibold transition"
            :class="lang === currentLang ? 'bg-primary text-white' : 'text-navy hover:bg-primary/5'"
            @click="selectLang(lang)"
          >
            {{ lang }}
          </button>
        </div>

        <BaseButton :to="cta.to" size="compact" class="mt-3 w-full py-3">
          {{ cta.label }}
        </BaseButton>
      </nav>
    </Transition>
  </div>
</template>

<style scoped>
.menu-enter-active,
.menu-leave-active {
  transition:
    opacity 0.18s ease,
    transform 0.18s ease;
}
.menu-enter-from,
.menu-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

/* Sidebar drawer: slide in from the right. */
.drawer-enter-active,
.drawer-leave-active {
  transition: transform 0.25s ease;
}
.drawer-enter-from,
.drawer-leave-to {
  transform: translateX(100%);
}

/* Sidebar backdrop: fade. */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .menu-enter-active,
  .menu-leave-active,
  .drawer-enter-active,
  .drawer-leave-active,
  .fade-enter-active,
  .fade-leave-active {
    transition: none;
  }
}
</style>
