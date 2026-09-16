import type { RouteRecordRaw } from 'vue-router'

const productRoutes: RouteRecordRaw[] = [
  {
    path: '/products/android',
    name: 'android-product',
    component: () => import('@/views/AndroidProduct.vue'),
  },
]

export default productRoutes
