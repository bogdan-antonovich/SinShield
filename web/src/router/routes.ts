import type { RouteRecordRaw } from 'vue-router'

import blogRoutes from '@/modules/blog/routes'
import legalRoutes from '@/modules/legal/routes'
import mainRoutes from '@/modules/main/routes'
import productRoutes from '@/modules/products/routes'
import androindRoutes from '@/modules/android/routes'

const routes: RouteRecordRaw[] = [
  ...mainRoutes,
  ...productRoutes,
  ...blogRoutes,
  ...androindRoutes,
  ...legalRoutes,
]

export default routes
