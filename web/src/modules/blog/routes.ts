import type { RouteRecordRaw } from 'vue-router'

const blogRoutes: RouteRecordRaw[] = [
  {
    path: '/blog',
    name: 'blog',
    component: () => import('@/views/Blog.vue'),
  },
  {
    path: '/blog/:slug',
    name: 'article',
    component: () => import('@/views/Article.vue'),
  },
]

export default blogRoutes
