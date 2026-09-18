import { createRouter, createWebHistory } from 'vue-router'

import routes from '@/router/routes'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash }

    // Force an instant jump so the global `scroll-behavior: smooth` on <html>
    // doesn't animate (and cut short) the reset, leaving the new page mid-scroll.
    return { top: 0, left: 0, behavior: 'instant' }
  },
})

export default router
