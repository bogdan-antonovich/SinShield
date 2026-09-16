<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

export interface TocEntry {
  id: string
  label: string
  children?: TocEntry[]
}

const props = defineProps<{
  entries: TocEntry[]
}>()

const activeId = ref('')

/** Flatten the nested entries into document order so the observer can pick the
 *  topmost heading that is currently in view. */
function flatten(entries: TocEntry[]): string[] {
  return entries.flatMap((entry) => [entry.id, ...flatten(entry.children ?? [])])
}

let observer: IntersectionObserver | undefined
const visible = new Map<string, boolean>()

/**
 * When the list is taller than its allotted space it scrolls, but instead of a
 * visible scrollbar or a hard clip the overflowing edge fades out. The fade is
 * suppressed at whichever end is fully reached so the first/last item stays crisp.
 */
const scroller = ref<HTMLElement>()
const fadeTop = ref(0)
const fadeBottom = ref(0)
const FADE_SIZE = 44
const ACTIVE_LINK_GAP = 8

function updateFade() {
  const el = scroller.value
  if (!el) return
  const { scrollTop, scrollHeight, clientHeight } = el
  fadeTop.value = scrollTop > 2 ? FADE_SIZE : 0
  fadeBottom.value = scrollTop + clientHeight < scrollHeight - 2 ? FADE_SIZE : 0
}

const maskStyle = computed(() => {
  const gradient =
    `linear-gradient(to bottom, transparent 0, #000 ${fadeTop.value}px, ` +
    `#000 calc(100% - ${fadeBottom.value}px), transparent 100%)`
  return { maskImage: gradient, WebkitMaskImage: gradient }
})

/** Keep the current section visible when the page scroll advances beyond the
 *  portion of the contents list that is on screen. */
function revealActiveLink() {
  const el = scroller.value
  const link = el?.querySelector<HTMLElement>('.toc-link.is-active')
  if (!el || !link) return

  const scrollerRect = el.getBoundingClientRect()
  const linkRect = link.getBoundingClientRect()
  const visibleTop = scrollerRect.top + fadeTop.value + ACTIVE_LINK_GAP
  const visibleBottom = scrollerRect.bottom - fadeBottom.value - ACTIVE_LINK_GAP

  let delta = 0
  if (linkRect.top < visibleTop) {
    delta = linkRect.top - visibleTop
  } else if (linkRect.bottom > visibleBottom) {
    delta = linkRect.bottom - visibleBottom
  }

  if (delta !== 0) {
    el.scrollTo({ top: el.scrollTop + delta, behavior: 'smooth' })
  }
}

watch(activeId, async () => {
  await nextTick()
  revealActiveLink()
})

onMounted(() => {
  const ids = flatten(props.entries)
  const elements = ids
    .map((id) => document.getElementById(id))
    .filter((el): el is HTMLElement => el !== null)

  if (elements.length === 0) return

  observer = new IntersectionObserver(
    (records) => {
      for (const record of records) {
        visible.set(record.target.id, record.isIntersecting)
      }

      const topmost = ids.find((id) => visible.get(id))
      if (topmost) {
        activeId.value = topmost
      }
    },
    // Offset the top for the fixed header and treat the upper third of the
    // viewport as the "reading" zone so the highlight tracks naturally.
    { rootMargin: '-96px 0px -68% 0px', threshold: 0 },
  )

  elements.forEach((el) => observer?.observe(el))

  // Seed the initial highlight before any scroll happens.
  if (ids[0]) {
    activeId.value = ids[0]
  }

  nextTick(updateFade)
  window.addEventListener('resize', updateFade)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  window.removeEventListener('resize', updateFade)
})

function handleClick(event: MouseEvent, id: string) {
  const target = document.getElementById(id)
  if (!target) return

  event.preventDefault()
  target.scrollIntoView({ behavior: 'smooth', block: 'start' })
  activeId.value = id
  history.replaceState(null, '', `#${id}`)
}
</script>

<template>
  <nav class="toc" aria-label="Table of contents">
    <p class="toc-title">Contents</p>

    <div ref="scroller" class="toc-scroll" :style="maskStyle" @scroll="updateFade">
      <ul class="toc-list">
      <li v-for="entry in entries" :key="entry.id">
        <a
          :href="`#${entry.id}`"
          class="toc-link"
          :class="{ 'is-active': activeId === entry.id }"
          @click="handleClick($event, entry.id)"
        >
          {{ entry.label }}
        </a>

        <ul v-if="entry.children?.length" class="toc-sublist">
          <li v-for="child in entry.children" :key="child.id">
            <a
              :href="`#${child.id}`"
              class="toc-link toc-sublink"
              :class="{ 'is-active': activeId === child.id }"
              @click="handleClick($event, child.id)"
            >
              {{ child.label }}
            </a>
          </li>
        </ul>
      </li>
      </ul>
    </div>
  </nav>
</template>

<style scoped>
.toc {
  border-left: 3px solid rgb(8 124 240 / 35%);
  padding-left: 1.25rem;
}

.toc-title {
  margin-bottom: 1.5rem;
  color: rgb(8 45 72 / 40%);
  font-family: var(--font-display);
  font-size: 0.78rem;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

/* Capped, self-scrolling list. The scrollbar is hidden and the overflowing
   edge is faded out by the mask bound in the template. Vertical padding keeps
   the first and last items from sitting under the fade. */
.toc-scroll {
  max-height: 60vh;
  overflow-y: auto;
  padding-block: 0.25rem;
  scrollbar-width: none;
}

.toc-scroll::-webkit-scrollbar {
  display: none;
}

.toc-list {
  display: flex;
  flex-direction: column;
  gap: 1.35rem;
  list-style: none;
}

.toc-link {
  display: block;
  color: rgb(8 45 72 / 38%);
  font-size: 0.95rem;
  font-weight: 600;
  line-height: 1.35;
  transition: color 0.15s ease;
}

.toc-link:hover {
  color: rgb(8 45 72 / 68%);
}

.toc-link.is-active {
  color: var(--ss-primary);
  font-weight: 700;
}

/* Nested sub-items carry a dashed guide line, mirroring the reference layout. */
.toc-sublist {
  margin-top: 1.1rem;
  margin-left: 0.35rem;
  padding-left: 1.15rem;
  border-left: 1px dashed rgb(8 45 72 / 15%);
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.toc-sublink {
  position: relative;
  font-size: 0.9rem;
  font-weight: 600;
}

.toc-sublink.is-active {
  border-radius: 0.5rem;
  background: rgb(8 124 240 / 6%);
  padding: 0.35rem 0.75rem;
}

/* A dot on the guide line marks the active sub-item. */
.toc-sublink.is-active::before {
  content: '';
  position: absolute;
  left: -1.5rem;
  top: 50%;
  width: 7px;
  height: 7px;
  border-radius: 9999px;
  background: var(--ss-primary);
  transform: translateY(-50%);
}
</style>
