<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import type { RouteLocationRaw } from 'vue-router'

import BaseButton from '@/common/components/BaseButton.vue'
import BaseContainer from '@/common/components/BaseContainer.vue'

interface ShowcaseItem {
  title: string
  description: string
  illustration: string
  illustrationAlt: string
  imageFit?: 'cover' | 'contain'
}

interface Props {
  id: string
  heading: string
  description: string
  items: ShowcaseItem[]
  tablistLabel: string
  theme?: 'dark' | 'light'
  ctaLabel?: string
  ctaTo?: RouteLocationRaw
}

const props = withDefaults(defineProps<Props>(), {
  theme: 'dark',
  ctaLabel: 'Get Started',
  ctaTo: '/products/android',
})

const activeIndex = ref(0)
const tabRefs = ref<HTMLButtonElement[]>([])
const panelId = computed(() => `${props.id}-panel`)

function tabId(index: number) {
  return `${props.id}-tab-${index}`
}

function setTabRef(element: unknown, index: number) {
  if (element instanceof HTMLButtonElement) tabRefs.value[index] = element
}

async function selectItem(index: number, moveFocus = false) {
  activeIndex.value = index
  if (moveFocus) {
    await nextTick()
    tabRefs.value[index]?.focus()
  }
}

function onTabKeydown(event: KeyboardEvent, index: number) {
  let nextIndex: number | null = null

  if (event.key === 'ArrowDown' || event.key === 'ArrowRight') {
    nextIndex = (index + 1) % props.items.length
  } else if (event.key === 'ArrowUp' || event.key === 'ArrowLeft') {
    nextIndex = (index - 1 + props.items.length) % props.items.length
  } else if (event.key === 'Home') {
    nextIndex = 0
  } else if (event.key === 'End') {
    nextIndex = props.items.length - 1
  }

  if (nextIndex !== null) {
    event.preventDefault()
    void selectItem(nextIndex, true)
  }
}
</script>

<template>
  <section
    :id="id"
    class="py-16 sm:py-20 lg:py-32"
    :class="theme === 'dark' ? 'app-shell' : 'bg-white'"
    :aria-labelledby="`${id}-heading`"
  >
    <BaseContainer>
      <div class="grid gap-14 lg:grid-cols-[minmax(0,0.92fr)_minmax(0,1.08fr)] lg:gap-20 xl:gap-28">
        <div
          :id="panelId"
          class="order-2 flex min-w-0 flex-col lg:order-1"
          role="tabpanel"
          :aria-labelledby="tabId(activeIndex)"
          tabindex="0"
        >
          <div
            class="aspect-[4/3] w-full overflow-hidden rounded-[1.75rem] shadow-2xl"
            :class="theme === 'dark' ? 'shadow-black/20' : 'shadow-primary/10'"
          >
            <img
              :src="items[activeIndex]?.illustration"
              :alt="items[activeIndex]?.illustrationAlt"
              width="800"
              height="600"
              draggable="false"
              class="h-full w-full select-none"
              :class="
                items[activeIndex]?.imageFit === 'contain'
                  ? 'object-contain p-8 sm:p-12'
                  : 'object-cover'
              "
            />
          </div>

          <div class="mt-8 max-w-xl lg:min-h-64 xl:min-h-56">
            <h3
              class="font-display text-2xl font-bold tracking-[-0.02em]"
              :class="theme === 'dark' ? 'text-white' : 'text-navy'"
            >
              {{ items[activeIndex]?.title }}
            </h3>
            <p
              class="mt-3 text-lg font-normal leading-[1.55] sm:text-xl"
              :class="theme === 'dark' ? 'text-white/75' : 'text-navy/70'"
            >
              {{ items[activeIndex]?.description }}
            </p>
          </div>

          <BaseButton
            :to="ctaTo"
            :variant="theme === 'dark' ? 'inverse' : 'primary'"
            class="mt-8 w-full sm:w-fit lg:mt-6"
          >
            {{ ctaLabel }}
          </BaseButton>
        </div>

        <div class="order-1 lg:order-2 lg:pt-1">
          <h2
            :id="`${id}-heading`"
            class="max-w-2xl font-display text-[34px] font-extrabold leading-[1.08] tracking-[-0.025em] sm:text-[44px] lg:text-[56px]"
            :class="theme === 'dark' ? 'text-white' : 'text-navy'"
          >
            {{ heading }}
          </h2>
          <p
            class="mt-6 max-w-2xl text-lg font-normal leading-[1.55] sm:text-xl"
            :class="theme === 'dark' ? 'text-white/75' : 'text-navy/70'"
          >
            {{ description }}
          </p>

          <div
            class="mt-10 border-t sm:mt-12"
            :class="theme === 'dark' ? 'border-white/20' : 'border-navy/15'"
            role="tablist"
            :aria-label="tablistLabel"
            aria-orientation="vertical"
          >
            <button
              v-for="(item, index) in items"
              :id="tabId(index)"
              :key="item.title"
              :ref="(element) => setTabRef(element, index)"
              type="button"
              role="tab"
              :aria-selected="activeIndex === index"
              :aria-controls="panelId"
              :tabindex="activeIndex === index ? 0 : -1"
              class="group flex min-h-14 w-full items-center border-b py-4 text-left sm:py-6"
              :class="theme === 'dark' ? 'border-white/20' : 'border-navy/15'"
              @click="selectItem(index)"
              @keydown="onTabKeydown($event, index)"
            >
              <span
                class="font-display text-xl font-bold leading-tight tracking-[-0.02em] sm:text-[26px] lg:text-[28px]"
                :class="
                  theme === 'dark'
                    ? activeIndex === index
                      ? 'text-white'
                      : 'text-white/35 group-hover:text-white/60'
                    : activeIndex === index
                      ? 'text-primary'
                      : 'text-navy/35 group-hover:text-navy/60'
                "
              >
                {{ item.title }}
              </span>
            </button>
          </div>
        </div>
      </div>
    </BaseContainer>
  </section>
</template>
