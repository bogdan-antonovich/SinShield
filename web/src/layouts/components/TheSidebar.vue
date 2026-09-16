<script setup lang="ts">
import { RouterLink } from 'vue-router'

interface NavLink {
  label: string
  to: string
}

const links: NavLink[] = [
  { label: 'Home', to: '/' },
  { label: 'Products', to: '/products' },
  { label: 'Blog', to: '/blog' },
  { label: 'About', to: '#about' },
]
</script>

<template>
  <aside
    class="fixed inset-y-0 left-0 z-40 flex w-36 flex-col border-r border-navy/10 bg-surface sm:w-44 lg:w-60 md:hidden"
  >
    <!-- Brand -->
    <div class="flex h-16 items-center px-4 lg:px-6">
      <RouterLink
        to="/"
        class="font-display text-lg font-extrabold tracking-tight lg:text-xl"
        aria-label="Home"
      >
        <span class="text-navy">Sin</span><span class="text-primary">Shield.</span>
      </RouterLink>
    </div>

    <!-- Navigation -->
    <nav class="flex flex-1 flex-col gap-1 px-3 py-4">
      <RouterLink
        v-for="link in links"
        :key="link.to"
        :to="link.to"
        class="rounded-lg px-3 py-2 font-display text-sm font-semibold text-navy transition hover:bg-primary/5 hover:text-primary"
        active-class="bg-primary/10 text-primary"
      >
        {{ link.label }}
      </RouterLink>
    </nav>
  </aside>
</template>
