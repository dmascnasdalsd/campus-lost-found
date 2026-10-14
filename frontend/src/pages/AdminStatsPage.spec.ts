import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import AdminStatsPage from './AdminStatsPage.vue'
import type { AdminStatsView } from '@/api/types'

// API 模块被内存 fake 替换：测试只断言「给定 API 返回，用户看到什么、点击后发出什么请求」。
const { getAdminStats, exportReportsCsv } = vi.hoisted(() => ({
  getAdminStats: vi.fn(),
  exportReportsCsv: vi.fn()
}))

vi.mock('@/api/admin', () => ({
  getAdminStats,
  exportReportsCsv
}))

const routes: RouteRecordRaw[] = [{ path: '/admin/stats', component: { template: '<div />' } }]

function statsView(overrides: Partial<AdminStatsView> = {}): AdminStatsView {
  return {
    range: 'week',
    from: '2026-10-08',
    to: '2026-10-14',
    totalReports: 12,
    returnedReports: 5,
    returnRate: 41.7,
    daily: [{ date: '2026-10-14', created: 3, returned: 1 }],
    byLocation: [{ name: '图书馆', count: 3 }],
    byCategory: [{ name: '校园卡', count: 2 }],
    byHour: [{ name: '09:00', count: 2 }],
    ...overrides
  }
}

async function mountPage() {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/admin/stats')
  await router.isReady()
  const wrapper = mount(AdminStatsPage, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

describe('AdminStatsPage', () => {
  beforeEach(() => {
    getAdminStats.mockReset()
    exportReportsCsv.mockReset()
    URL.createObjectURL = vi.fn(() => 'blob:fake')
    URL.revokeObjectURL = vi.fn()
  })

  it('按 mock 数据渲染数字卡片', async () => {
    getAdminStats.mockResolvedValue(statsView())

    const wrapper = await mountPage()

    expect(getAdminStats).toHaveBeenCalledWith('week')
    expect(wrapper.find('.stats-total').text()).toBe('12')
    expect(wrapper.find('.stats-returned').text()).toBe('5')
    expect(wrapper.find('.stats-rate').text()).toBe('41.7%')
  })

  it('分布与每日序列按 mock 数据渲染', async () => {
    getAdminStats.mockResolvedValue(statsView())

    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('图书馆')
    expect(wrapper.text()).toContain('校园卡')
    expect(wrapper.text()).toContain('09:00')
    expect(wrapper.text()).toContain('2026-10-14')
  })

  it('切换到月时以 month 重新请求', async () => {
    getAdminStats.mockResolvedValue(statsView())

    const wrapper = await mountPage()
    await wrapper.find('.range-month').trigger('click')
    await flushPromises()

    expect(getAdminStats).toHaveBeenLastCalledWith('month')
  })

  it('点击导出调用 exportReportsCsv', async () => {
    getAdminStats.mockResolvedValue(statsView())
    exportReportsCsv.mockResolvedValue(new Blob(['x'], { type: 'text/csv' }))
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    const wrapper = await mountPage()
    await wrapper.find('.stats-export').trigger('click')
    await flushPromises()

    expect(exportReportsCsv).toHaveBeenCalledTimes(1)
  })

  it('无数据时显示中文空态', async () => {
    getAdminStats.mockResolvedValue(
      statsView({
        totalReports: 0,
        returnedReports: 0,
        returnRate: 0,
        daily: [],
        byLocation: [],
        byCategory: [],
        byHour: []
      })
    )

    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('暂无统计数据')
  })
})
