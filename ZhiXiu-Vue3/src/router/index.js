import { createRouter, createWebHistory } from 'vue-router'
import { adminRoutes, constantRoutes, ownerRoutes, workerRoutes } from './routes'
import { setupRouterGuard } from './guard'

const routes = [
  { path: '/', redirect: '/login' },
  ...constantRoutes,
  ownerRoutes,
  workerRoutes,
  adminRoutes,
  { path: '/:pathMatch(.*)*', redirect: '/404' }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: (to, from, savedPosition) => savedPosition || { top: 0 }
})

setupRouterGuard(router)

export default router
