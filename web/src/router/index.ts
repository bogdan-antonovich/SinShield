import { createRouter, createWebHistory } from 'vue-router'

import routes from '@/router/routes'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash }

    return { top: 0, left: 0 }
  },
})

export default router
