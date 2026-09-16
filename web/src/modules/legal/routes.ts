import type { RouteRecordRaw } from 'vue-router'

const legalRoutes: RouteRecordRaw[] = [
  {
    path: '/privacy-policy',
    name: 'privacy-policy',
    component: () => import('@/views/PrivacyPolicy.vue'),
  },
  {
    path: '/terms-and-conditions',
    name: 'terms-and-conditions',
    component: () => import('@/views/TermsAndConditions.vue'),
  },
]

export default legalRoutes
