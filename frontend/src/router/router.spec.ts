import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { authGuard, routes } from './index'
import { useAuthStore } from '@/stores/auth'

// 单独构造 router 验证守卫行为：未登录访问受保护路由应被重定向并带上回跳参数。
function makeRouter() {
  const router = createRouter({ history: createMemoryHistory(), routes })
  router.beforeEach(authGuard)
  return router
}

describe('路由守卫', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
  })

  it('未登录访问 /reports/new 被重定向到登录页并携带 redirect', async () => {
    const router = makeRouter()

    await router.push('/reports/new')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/reports/new')
  })

  it('已登录时可直接进入受保护路由', async () => {
    const auth = useAuthStore()
    auth.token = 'token-1'
    const router = makeRouter()

    await router.push('/reports/new')
    await router.isReady()

    expect(router.currentRoute.value.path).toBe('/reports/new')
  })
})
