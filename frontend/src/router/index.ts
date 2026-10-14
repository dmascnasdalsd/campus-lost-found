import { createRouter, createWebHistory } from 'vue-router'
import type { NavigationGuardWithThis, RouteRecordRaw } from 'vue-router'
import HomePage from '@/pages/HomePage.vue'
import LoginPage from '@/pages/LoginPage.vue'
import RegisterPage from '@/pages/RegisterPage.vue'
import ReportsNewPage from '@/pages/ReportsNewPage.vue'
import ReportDetailPage from '@/pages/ReportDetailPage.vue'
import { useAuthStore } from '@/stores/auth'

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'home',
    component: HomePage
  },
  {
    path: '/login',
    name: 'login',
    component: LoginPage
  },
  {
    path: '/register',
    name: 'register',
    component: RegisterPage
  },
  {
    path: '/reports/new',
    name: 'report-new',
    component: ReportsNewPage,
    meta: { requiresAuth: true }
  },
  {
    path: '/reports/:id',
    name: 'report-detail',
    component: ReportDetailPage
  }
]

/**
 * 未登录访问受保护路由时，重定向到登录页并带上原路径，登录成功后回跳。
 */
export const authGuard: NavigationGuardWithThis<undefined> = (to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
}

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(authGuard)

export default router
