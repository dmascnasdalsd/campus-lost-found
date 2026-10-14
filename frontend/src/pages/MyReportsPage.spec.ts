import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import MyReportsPage from './MyReportsPage.vue'
import type { ReportSummary } from '@/api/types'

// API client 模块被内存 fake 替换：只断言「给定 API 返回，用户看到什么、点击后发出什么请求」。
const { listMyReports, updateReportStatus, deleteReport, getReport, updateReport } = vi.hoisted(() => ({
  listMyReports: vi.fn(),
  updateReportStatus: vi.fn(),
  deleteReport: vi.fn(),
  getReport: vi.fn(),
  updateReport: vi.fn()
}))

vi.mock('@/api/reports', () => ({
  listMyReports,
  updateReportStatus,
  deleteReport,
  getReport,
  updateReport
}))

function summary(overrides: Partial<ReportSummary> = {}): ReportSummary {
  return {
    id: 1,
    type: 'LOST',
    title: '校园卡',
    category: '校园卡',
    location: '图书馆',
    locationDetail: null,
    eventTime: '2026-10-14T08:30:00',
    status: 'OPEN',
    pinned: false,
    custody: null,
    thumbnailPath: null,
    viewCount: 0,
    // 默认在编辑窗口内（发布 5 分钟前）。
    createdAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
    ...overrides
  }
}

async function mountPage(items: ReportSummary[]) {
  listMyReports.mockResolvedValue({ items, page: 0, size: 20, total: items.length })
  const wrapper = mount(MyReportsPage)
  await flushPromises()
  return wrapper
}

describe('MyReportsPage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    listMyReports.mockReset()
    updateReportStatus.mockReset()
    deleteReport.mockReset()
    getReport.mockReset()
    updateReport.mockReset()
  })

  it('OPEN 启事渲染状态徽标与编辑 / 删除 / 归还按钮', async () => {
    const wrapper = await mountPage([summary()])

    expect(wrapper.text()).toContain('招领中')
    expect(wrapper.find('.action-edit').exists()).toBe(true)
    expect(wrapper.find('.action-delete').exists()).toBe(true)
    expect(wrapper.find('.action-return').exists()).toBe(true)
  })

  it('RETURNED 启事渲染已归还徽标且不显示编辑 / 删除 / 归还操作', async () => {
    const wrapper = await mountPage([summary({ status: 'RETURNED' })])

    expect(wrapper.text()).toContain('已归还')
    expect(wrapper.find('.action-edit').exists()).toBe(false)
    expect(wrapper.find('.action-delete').exists()).toBe(false)
    expect(wrapper.find('.action-return').exists()).toBe(false)
  })

  it('招领且自留时显示标记已移交按钮，已交保卫处时不显示', async () => {
    const self = await mountPage([summary({ type: 'FOUND', custody: 'SELF' })])
    expect(self.find('.action-handover').exists()).toBe(true)

    const security = await mountPage([summary({ type: 'FOUND', custody: 'AT_SECURITY' })])
    expect(security.find('.action-handover').exists()).toBe(false)
  })

  it('编辑窗口内显示剩余时间，超时后禁用编辑 / 删除', async () => {
    const wrapper = await mountPage([summary()])
    expect(wrapper.find('.edit-window').text()).toContain('可编辑')
    expect(wrapper.find('.action-edit').attributes('disabled')).toBeUndefined()

    const expired = await mountPage([
      summary({ createdAt: new Date(Date.now() - 31 * 60 * 1000).toISOString() })
    ])
    expect(expired.find('.edit-window').text()).toContain('已超过 30 分钟')
    expect(expired.find('.action-edit').attributes('disabled')).toBeDefined()
    expect(expired.find('.action-delete').attributes('disabled')).toBeDefined()
  })

  it('确认后点击标记归还发出正确请求', async () => {
    const wrapper = await mountPage([summary()])
    updateReportStatus.mockResolvedValue({})
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    await wrapper.find('.action-return').trigger('click')
    await flushPromises()

    expect(updateReportStatus).toHaveBeenCalledWith(1, 'RETURNED')
    expect(listMyReports).toHaveBeenCalledTimes(2)
  })

  it('取消确认时不发出标记归还请求', async () => {
    const wrapper = await mountPage([summary()])
    vi.spyOn(window, 'confirm').mockReturnValue(false)

    await wrapper.find('.action-return').trigger('click')
    await flushPromises()

    expect(updateReportStatus).not.toHaveBeenCalled()
  })

  it('填写移交单号并确认后发出已移交请求', async () => {
    const wrapper = await mountPage([summary({ type: 'FOUND', custody: 'SELF' })])
    updateReportStatus.mockResolvedValue({})
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    await wrapper.find('.action-handover').trigger('click')
    await flushPromises()
    await wrapper.find('.handover-no').setValue('HY-2026-001')
    await wrapper.find('.handover-confirm').trigger('click')
    await flushPromises()

    expect(updateReportStatus).toHaveBeenCalledWith(1, 'HANDED_OVER', 'HY-2026-001')
  })
})
