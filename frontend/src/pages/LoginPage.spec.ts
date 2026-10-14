import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import LoginPage from './LoginPage.vue'
import { ApiError } from '@/api/client'

// API client 模块被内存 fake 替换：测试只断言「给定 API 返回，用户看到什么 / 发出什么请求」。
const { login, register, me, logout } = vi.hoisted(() => ({
  login: vi.fn(),
  register: vi.fn(),
  me: vi.fn(),
  logout: vi.fn()
}))

vi.mock('@/api/auth', () => ({ login, register, me, logout }))

const stub = { template: '<div />' }

const routes: RouteRecordRaw[] = [
  { path: '/', component: stub },
  { path: '/login', component: LoginPage },
  { path: '/register', component: stub },
  { path: '/reports/new', component: stub }
]

async function mountAtLogin() {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/login?redirect=/reports/new')
  await router.isReady()
  const wrapper = mount(LoginPage, {
    global: { plugins: [createPinia(), router] }
  })
  return { wrapper, router }
}

describe('LoginPage', () => {
  beforeEach(() => {
    login.mockReset()
    localStorage.clear()
  })

  it('空表单提交时显示字段级错误且不调用接口', async () => {
    const { wrapper } = await mountAtLogin()

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('请输入学号')
    expect(wrapper.text()).toContain('请输入密码')
    expect(login).not.toHaveBeenCalled()
  })

  it('登录成功后跳转到 redirect 指向的页面', async () => {
    login.mockResolvedValue({
      token: 'token-1',
      user: {
        id: 1,
        studentNo: '2021001',
        nickname: '张三',
        department: null,
        email: null,
        role: 'USER',
        status: 'ACTIVE',
        contactType: null,
        contactValue: null,
        contactPublic: false,
        createdAt: '2026-10-14T09:00:00'
      }
    })
    const { wrapper, router } = await mountAtLogin()

    await wrapper.find('input[type="text"]').setValue('2021001')
    await wrapper.find('input[type="password"]').setValue('secret123')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(login).toHaveBeenCalledWith({ studentNo: '2021001', password: 'secret123' })
    expect(router.currentRoute.value.fullPath).toBe('/reports/new')
  })

  it('接口返回业务错误时显示错误信息', async () => {
    login.mockRejectedValue(new ApiError(1003, '学号或密码不正确'))
    const { wrapper } = await mountAtLogin()

    await wrapper.find('input[type="text"]').setValue('2021001')
    await wrapper.find('input[type="password"]').setValue('wrong-pass')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('学号或密码不正确')
  })
})
