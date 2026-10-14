import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import AdminReportsPage from './AdminReportsPage.vue'
import type { PageResult, ReportDetail } from '@/api/types'

// API 模块被内存 fake 替换：测试只断言「给定 API 返回，用户看到什么、点击后发出什么请求」。
const { listAdminReports, approveReport, rejectReport, takeDownReport, pinReport, unpinReport } =
  vi.hoisted(() => ({
    listAdminReports: vi.fn(),
    approveReport: vi.fn(),
    rejectReport: vi.fn(),
    takeDownReport: vi.fn(),
    pinReport: vi.fn(),
    unpinReport: vi.fn()
  }))

vi.mock('@/api/admin', () => ({
  listAdminReports,
  approveReport,
  rejectReport,
  takeDownReport,
  pinReport,
  unpinReport
}))

const routes: RouteRecordRaw[] = [
  { path: '/admin/reports', component: { template: '<div />' } },
  { path: '/reports/:id', component: { template: '<div />' } }
]

function reportDetail(overrides: Partial<ReportDetail> = {}): ReportDetail {
  return {
    id: 1,
    type: 'FOUND',
    title: '捡到的校园卡',
    category: '校园卡',
    location: '图书馆',
    locationDetail: null,
    eventTime: null,
    description: '在图书馆一楼拾到',
    status: 'PENDING_REVIEW',
    pinned: false,
    custody: 'SELF',
    handoverNo: null,
    viewCount: 0,
    publisher: null,
    images: [],
    rejectReason: null,
    reviewedAt: null,
    createdAt: '2026-10-14T09:00:00',
    ...overrides
  }
}

function page(items: ReportDetail[]): PageResult<ReportDetail> {
  return { items, page: 0, size: 20, total: items.length }
}

async function mountPage() {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/admin/reports')
  await router.isReady()
  const wrapper = mount(AdminReportsPage, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

describe('AdminReportsPage', () => {
  beforeEach(() => {
    listAdminReports.mockReset()
    approveReport.mockReset()
    rejectReport.mockReset()
    takeDownReport.mockReset()
    pinReport.mockReset()
    unpinReport.mockReset()
  })

  it('默认按待复核筛选并渲染队列中的启事标题', async () => {
    listAdminReports.mockResolvedValue(page([reportDetail()]))

    const wrapper = await mountPage()

    expect(listAdminReports).toHaveBeenCalledWith('PENDING_REVIEW')
    expect(wrapper.text()).toContain('捡到的校园卡')
    expect(wrapper.text()).toContain('待复核')
  })

  it('待复核启事显示通过、驳回、下架按钮', async () => {
    listAdminReports.mockResolvedValue(page([reportDetail({ status: 'PENDING_REVIEW' })]))

    const wrapper = await mountPage()

    expect(wrapper.find('.admin-card__approve').exists()).toBe(true)
    expect(wrapper.find('.admin-card__reject').exists()).toBe(true)
    expect(wrapper.find('.admin-card__takedown').exists()).toBe(true)
  })

  it('普通启事不显示通过按钮，而显示下架与置顶', async () => {
    listAdminReports.mockResolvedValue(page([reportDetail({ status: 'OPEN' })]))

    const wrapper = await mountPage()

    expect(wrapper.find('.admin-card__approve').exists()).toBe(false)
    expect(wrapper.find('.admin-card__takedown').exists()).toBe(true)
    expect(wrapper.find('.admin-card__pin').exists()).toBe(true)
  })

  it('点击通过调用接口并重新加载队列', async () => {
    listAdminReports.mockResolvedValue(page([reportDetail({ id: 7 })]))
    approveReport.mockResolvedValue(reportDetail({ id: 7, status: 'OPEN' }))
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const wrapper = await mountPage()
    await wrapper.find('.admin-card__approve').trigger('click')
    await flushPromises()

    expect(approveReport).toHaveBeenCalledWith(7)
    expect(listAdminReports).toHaveBeenCalledTimes(2)
  })

  it('切换到全部按无状态参数请求', async () => {
    listAdminReports.mockResolvedValue(page([reportDetail()]))

    const wrapper = await mountPage()
    await wrapper.find('.filter-all').trigger('click')
    await flushPromises()

    expect(listAdminReports).toHaveBeenLastCalledWith(undefined)
  })

  it('下架需填写原因，空原因时不发出请求', async () => {
    listAdminReports.mockResolvedValue(page([reportDetail({ id: 3 })]))
    takeDownReport.mockResolvedValue(reportDetail({ id: 3, status: 'OFFLINE' }))

    const wrapper = await mountPage()
    await wrapper.find('.admin-card__takedown').trigger('click')
    await flushPromises()

    expect(wrapper.find('.reason-modal').exists()).toBe(true)
    await wrapper.find('.reason-modal__submit').trigger('click')
    await flushPromises()

    expect(takeDownReport).not.toHaveBeenCalled()
    expect(wrapper.find('.reason-modal__error').text()).toContain('请填写下架原因')

    await wrapper.find('.reason-modal__reason').setValue('包含违规内容')
    await wrapper.find('.reason-modal__submit').trigger('click')
    await flushPromises()

    expect(takeDownReport).toHaveBeenCalledWith(3, '包含违规内容')
    expect(listAdminReports).toHaveBeenCalledTimes(2)
  })
})
