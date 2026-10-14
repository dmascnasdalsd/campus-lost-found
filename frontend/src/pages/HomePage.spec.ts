import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { Router } from 'vue-router'
import HomePage from './HomePage.vue'

// API client 模块被内存 fake 替换：测试只断言「给定 API 返回，用户看到什么、发出什么请求」。
const { listReports } = vi.hoisted(() => ({ listReports: vi.fn() }))

vi.mock('@/api/reports', () => ({ listReports }))

const oneReport = {
  id: 1,
  type: 'LOST' as const,
  title: '校园卡',
  category: '校园卡',
  location: '图书馆',
  locationDetail: null,
  eventTime: '2026-10-14T08:30:00',
  status: 'OPEN' as const,
  pinned: false,
  thumbnailPath: null,
  viewCount: 3,
  createdAt: '2026-10-14T09:00:00'
}

function makeRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: { template: '<div />' } }]
  })
}

async function mountHome(router: Router, query: Record<string, string> = {}) {
  await router.push({ path: '/', query })
  await router.isReady()
  const wrapper = mount(HomePage, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

describe('HomePage', () => {
  beforeEach(() => {
    listReports.mockReset()
  })

  it('无启事时渲染空状态', async () => {
    listReports.mockResolvedValue({ items: [], page: 0, size: 10, total: 0 })

    const wrapper = await mountHome(makeRouter())

    expect(wrapper.text()).toContain('还没有启事')
    expect(wrapper.find('.report-list').exists()).toBe(false)
  })

  it('有启事时渲染卡片列表', async () => {
    listReports.mockResolvedValue({ items: [oneReport], page: 0, size: 10, total: 1 })

    const wrapper = await mountHome(makeRouter())

    const cards = wrapper.findAll('.report-card')
    expect(cards).toHaveLength(1)
    expect(cards[0].text()).toContain('校园卡')
    expect(cards[0].text()).toContain('失物')
    expect(cards[0].text()).toContain('图书馆')
  })

  it('请求失败时渲染错误态而不是空状态', async () => {
    listReports.mockRejectedValue(new Error('network down'))

    const wrapper = await mountHome(makeRouter())

    expect(wrapper.text()).toContain('加载失败')
    expect(wrapper.text()).not.toContain('还没有启事')
  })

  it('进入页面时从 URL query 初始化筛选并据此请求', async () => {
    listReports.mockResolvedValue({ items: [], page: 0, size: 10, total: 0 })

    await mountHome(makeRouter(), { type: 'LOST', keyword: 'AirPods', category: '耳机' })

    expect(listReports).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'LOST', keyword: 'AirPods', category: '耳机', size: 10 })
    )
  })

  it('改筛选后按新条件请求', async () => {
    listReports.mockResolvedValue({ items: [], page: 0, size: 10, total: 0 })
    const router = makeRouter()
    const wrapper = await mountHome(router)

    await wrapper.find('.filter-category').setValue('校园卡')
    await flushPromises()

    expect(listReports).toHaveBeenLastCalledWith(expect.objectContaining({ category: '校园卡', page: 0, size: 10 }))
  })

  it('筛选条件同步到 URL query', async () => {
    listReports.mockResolvedValue({ items: [], page: 0, size: 10, total: 0 })
    const router = makeRouter()
    const wrapper = await mountHome(router)

    await wrapper.find('.filter-location').setValue('图书馆')
    await flushPromises()

    expect(router.currentRoute.value.query.location).toBe('图书馆')
  })

  it('勾选含已结束后请求带 includeClosed 且 URL 同步', async () => {
    listReports.mockResolvedValue({ items: [], page: 0, size: 10, total: 0 })
    const router = makeRouter()
    const wrapper = await mountHome(router)

    await wrapper.find('.filter-include-closed').setValue(true)
    await flushPromises()

    expect(listReports).toHaveBeenLastCalledWith(expect.objectContaining({ includeClosed: true }))
    expect(router.currentRoute.value.query.includeClosed).toBe('true')
  })
})
