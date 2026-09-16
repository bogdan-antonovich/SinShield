<script setup lang="ts">
import { nextTick, ref } from 'vue'
import BaseButton from '@/common/components/BaseButton.vue'
import BaseContainer from '@/common/components/BaseContainer.vue'

interface Step {
  title: string
  description: string
}

const steps: Step[] = [
  {
    title: 'A screen change starts a scan',
    description:
      'When a protected app reports a window or content change, SinShield schedules a fresh capture. Rapid changes are combined so the scanner analyzes the newest visible frame instead of queuing stale frames.',
  },
  {
    title: 'The visible frame is analyzed',
    description:
      'The captured frame is classified on the device. SinShield scores both the full screen and detected media regions for explicit and suggestive content; unchanged known-safe frames are skipped.',
  },
  {
    title: 'Uncertain detections are checked again',
    description:
      'A clear unsafe score produces a final verdict. Borderline detections receive a provisional cover and a quick follow-up scan; an independent verifier is used when the policy requires another signal.',
  },
  {
    title: 'Unsafe content is covered',
    description:
      'A final unsafe verdict places a full-screen shield in supported apps or a cover over the detected media region. Separately, the local DNS filter rejects requests for known adult domains before those sites can load.',
  },
]

const activeIndex = ref(0)
const tabRefs = ref<HTMLButtonElement[]>([])

function setTabRef(element: unknown, index: number) {
  if (element instanceof HTMLButtonElement) tabRefs.value[index] = element
}

async function selectStep(index: number, moveFocus = false) {
  activeIndex.value = index
  if (moveFocus) {
    await nextTick()
    tabRefs.value[index]?.focus()
  }
}

function onTabKeydown(event: KeyboardEvent, index: number) {
  let nextIndex: number | null = null

  if (event.key === 'ArrowDown' || event.key === 'ArrowRight') {
    nextIndex = (index + 1) % steps.length
  } else if (event.key === 'ArrowUp' || event.key === 'ArrowLeft') {
    nextIndex = (index - 1 + steps.length) % steps.length
  } else if (event.key === 'Home') {
    nextIndex = 0
  } else if (event.key === 'End') {
    nextIndex = steps.length - 1
  }

  if (nextIndex !== null) {
    event.preventDefault()
    void selectStep(nextIndex, true)
  }
}
</script>

<template>
  <section id="how-it-works" class="app-shell py-16 sm:py-20 lg:py-32">
    <BaseContainer>
      <div class="grid gap-14 lg:grid-cols-[minmax(0,0.92fr)_minmax(0,1.08fr)] lg:gap-20 xl:gap-28">
        <div
          id="how-it-works-panel"
          class="flex min-w-0 flex-col"
          role="tabpanel"
          :aria-labelledby="`how-it-works-tab-${activeIndex}`"
          tabindex="0"
        >
          <div
            class="aspect-[4/3] overflow-hidden rounded-[1.75rem] bg-navy shadow-2xl shadow-navy/20"
          >
            <img
              src="/images/how-it-works.png"
              alt="Android phone on a calm desk, representing private on-device protection"
              class="h-full w-full object-cover"
            />
          </div>

          <div class="mt-7 max-w-xl sm:mt-8 lg:min-h-64 xl:min-h-56">
            <h3 class="font-display text-2xl font-bold tracking-[-0.02em] text-white">
              {{ steps[activeIndex]?.title }}
            </h3>
            <p class="mt-3 text-lg font-normal leading-[1.55] text-white/75 sm:text-xl">
              {{ steps[activeIndex]?.description }}
            </p>
          </div>

          <BaseButton to="/products/android" variant="inverse" class="mt-8 w-full sm:w-fit lg:mt-6">
            Get Started
          </BaseButton>
        </div>

        <div class="lg:pt-1">
          <h2
            class="max-w-2xl font-display text-[34px] font-extrabold leading-[1.08] tracking-[-0.025em] text-white sm:text-[44px] lg:text-[56px]"
          >
            How SinShield Works
          </h2>

          <p class="mt-6 max-w-2xl text-lg font-normal leading-[1.55] text-white/75 sm:text-xl">
            Inside protected apps, SinShield responds to screen changes, captures the current frame,
            analyzes it with on-device models, and covers content that receives an unsafe verdict.
            Website protection checks DNS requests against an adult-domain list and rejects matches
            before the site loads.
          </p>

          <div
            class="mt-10 border-t border-white/20 sm:mt-12"
            role="tablist"
            aria-label="How SinShield works"
            aria-orientation="vertical"
          >
            <button
              v-for="(step, index) in steps"
              :id="`how-it-works-tab-${index}`"
              :key="step.title"
              :ref="(element) => setTabRef(element, index)"
              type="button"
              role="tab"
              :aria-selected="activeIndex === index"
              aria-controls="how-it-works-panel"
              :tabindex="activeIndex === index ? 0 : -1"
              class="group flex min-h-14 w-full items-center border-b border-white/20 py-4 text-left sm:py-6"
              @click="selectStep(index)"
              @keydown="onTabKeydown($event, index)"
            >
              <span
                class="font-display text-xl font-bold leading-tight tracking-[-0.02em] sm:text-[26px] lg:text-[28px]"
                :class="
                  activeIndex === index ? 'text-white' : 'text-white/35 group-hover:text-white/60'
                "
              >
                {{ step.title }}
              </span>
            </button>
          </div>
        </div>
      </div>
    </BaseContainer>
  </section>
</template>
