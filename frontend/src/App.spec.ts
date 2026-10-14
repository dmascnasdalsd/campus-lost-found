import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import App from './App.vue'

const routes: RouteRecordRaw[] = [
  { path: '/', component: { template: '<div />' } },
  { path: '/login', component: { template: '<div />' } },
  { path: '/register', component: { template: '<div />' } },
  { path: '/me/reports', component: { template: '<div />' } },
  { path: '/me/claims', component: { template: '<div />' } },
  { path: '/me/chats', component: { template: '<div />' } },
  { path: '/notifications', component: { template: '<div />' } },
  { path: '/admin/reports', component: { template: '<div />' } },
  { path: '/admin/flags', component: { template: '<div />' } },
  { path: '/admin/stats', component: { template: '<div />' } }
]

/** jsdom 默认没有 matchMedia，测试里显式 stub 以模拟窄屏 / 宽屏。 */
function stubMatchMedia(matches: boolean): void {
  window.matchMedia = vi.fn().mockImplementation((query: string) => ({
    matches,
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn()
  })) as unknown as typeof window.matchMedia
}

async function mountApp() {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/')
  await router.isReady()
  const wrapper = mount(App, { global: { plugins: [createPinia(), router] } })
  await flushPromises()
  return wrapper
}

describe('App 导航响应式', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('窄屏显示「菜单」按钮，点击后展开导航链接', async () => {
    stubMatchMedia(true)
    const wrapper = await mountApp()

    expect(wrapper.find('.app__menu-toggle').exists()).toBe(true)
    // 折叠状态下导航默认收起（v-show 的内联 display: none）。
    expect(wrapper.find('.app__actions').attributes('style')).toContain('display: none')

    await wrapper.find('.app__menu-toggle').trigger('click')
    await flushPromises()

    expect(wrapper.find('.app__menu-toggle').attributes('aria-expanded')).toBe('true')
    expect(wrapper.find('.app__actions').attributes('style') ?? '').not.toContain('display: none')
    expect(wrapper.text()).toContain('登录')
  })

  it('宽屏不显示菜单按钮，导航链接直接可见', async () => {
    stubMatchMedia(false)
    const wrapper = await mountApp()

    expect(wrapper.find('.app__menu-toggle').exists()).toBe(false)
    expect(wrapper.find('.app__actions').attributes('style') ?? '').not.toContain('display: none')
    expect(wrapper.text()).toContain('登录')
  })
})
