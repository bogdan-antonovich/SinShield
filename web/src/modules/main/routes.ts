import type { RouteRecordRaw } from 'vue-router'

const mainRoutes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: () => import('@/views/Home.vue'),
  },
  {
    path: '/about',
    name: 'about',
    component: () => import('@/views/About.vue'),
  },
  {
    path: '/editorial-standards',
    name: 'editorial-standards',
    component: () => import('@/views/EditorialStandards.vue'),
  },
  {
    path: '/authors',
    name: 'authors',
    component: () => import('@/views/Authors.vue'),
  },
  {
    path: '/authors/sinshield-editorial',
    name: 'sinshield-editorial',
    component: () => import('@/views/SinShieldEditorial.vue'),
  },
]

export default mainRoutes
