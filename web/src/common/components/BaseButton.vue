<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, type RouteLocationRaw } from 'vue-router'

type ButtonVariant = 'primary' | 'inverse'
type ButtonSize = 'default' | 'compact'

interface Props {
  to?: RouteLocationRaw
  href?: string
  type?: 'button' | 'submit' | 'reset'
  variant?: ButtonVariant
  size?: ButtonSize
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  type: 'button',
  variant: 'primary',
  size: 'default',
  disabled: false,
})

const classes = computed(() => [
  'base-button inline-flex items-center justify-center rounded-full font-display',
  'focus-visible:outline-none focus-visible:ring-3 disabled:pointer-events-none disabled:opacity-50',
  props.size === 'default'
    ? 'min-h-13 px-7 text-center text-[15px] font-bold sm:min-h-14 sm:px-10 sm:text-base'
    : 'min-h-10 px-5 text-sm font-semibold',
  props.variant === 'primary'
    ? 'base-button--primary bg-primary text-white focus-visible:ring-primary/35'
    : 'base-button--inverse bg-white text-primary focus-visible:ring-white/40',
])
</script>

<template>
  <RouterLink v-if="to" :to="to" :class="classes">
    <slot />
  </RouterLink>

  <a v-else-if="href" :href="href" :class="classes">
    <slot />
  </a>

  <button v-else :type="type" :disabled="disabled" :class="classes">
    <slot />
  </button>
</template>

<style scoped>
.base-button {
  --button-edge: var(--ss-primary-dark);
  --button-shadow: rgb(8 45 72 / 20%);

  box-shadow:
    0 6px 0 var(--button-edge),
    0 11px 20px var(--button-shadow);
  transform: translateY(0);
  transition:
    background-color 160ms ease,
    box-shadow 110ms ease,
    transform 110ms ease;
  -webkit-tap-highlight-color: transparent;
}

.base-button--inverse {
  --button-edge: #bfd2e8;
  --button-shadow: rgb(8 45 72 / 24%);
}

.base-button:hover {
  box-shadow:
    0 5px 0 var(--button-edge),
    0 9px 16px var(--button-shadow);
  transform: translateY(1px);
}

.base-button--primary:hover {
  background-color: #1989f5;
}

.base-button--inverse:hover {
  background-color: var(--color-background-soft);
}

.base-button:active {
  box-shadow:
    0 1px 0 var(--button-edge),
    0 3px 6px var(--button-shadow);
  transform: translateY(5px);
}

.base-button--primary:active {
  background-color: var(--ss-primary-dark);
}

@media (prefers-reduced-motion: reduce) {
  .base-button {
    transition: background-color 160ms ease;
  }
}
</style>
