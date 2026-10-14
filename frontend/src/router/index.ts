import { createRouter, createWebHistory } from 'vue-router'
import type { NavigationGuardWithThis, RouteRecordRaw } from 'vue-router'
import HomePage from '@/pages/HomePage.vue'
import LoginPage from '@/pages/LoginPage.vue'
import RegisterPage from '@/pages/RegisterPage.vue'
import ReportsNewPage from '@/pages/ReportsNewPage.vue'
import ReportDetailPage from '@/pages/ReportDetailPage.vue'
import MyReportsPage from '@/pages/MyReportsPage.vue'
import MyClaimsPage from '@/pages/MyClaimsPage.vue'
import MyChatsPage from '@/pages/MyChatsPage.vue'
import ChatPage from '@/pages/ChatPage.vue'
import AdminReportsPage from '@/pages/AdminReportsPage.vue'
import AdminAuditLogsPage from '@/pages/AdminAuditLogsPage.vue'
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
  },
  {
    path: '/me/reports',
    name: 'my-reports',
    component: MyReportsPage,
    meta: { requiresAuth: true }
  },
  {
    path: '/me/claims',
    name: 'my-claims',
    component: MyClaimsPage,
    meta: { requiresAuth: true }
  },
  {
    path: '/me/chats',
    name: 'my-chats',
    component: MyChatsPage,
    meta: { requiresAuth: true }
  },
  {
    path: '/me/chats/:id',
    name: 'chat',
    component: ChatPage,
    meta: { requiresAuth: true }
  },
  {
    path: '/admin/reports',
    name: 'admin-reports',
    component: AdminReportsPage,
    meta: { requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/admin/audit-logs',
    name: 'admin-audit-logs',
    component: AdminAuditLogsPage,
    meta: { requiresAuth: true, requiresAdmin: true }
  }
]

/**
 * 未登录访问受保护路由时，重定向到登录页并带上原路径，登录成功后回跳。
 * 需要管理员的路由在已登录但非 ADMIN 时重定向到首页。
 */
export const authGuard: NavigationGuardWithThis<undefined> = (to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresAdmin && auth.user?.role !== 'ADMIN') {
    return { path: '/' }
  }
  return true
}

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(authGuard)

export default router
