import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdminAuditLogsPage from './AdminAuditLogsPage.vue'
import type { AuditLogView, PageResult } from '@/api/types'

// API 模块被内存 fake 替换：测试只断言「给定审计记录，用户看到什么」。
const { listAuditLogs } = vi.hoisted(() => ({ listAuditLogs: vi.fn() }))

vi.mock('@/api/admin', () => ({ listAuditLogs }))

function auditLog(overrides: Partial<AuditLogView> = {}): AuditLogView {
  return {
    id: 1,
    operatorId: 9,
    operatorNickname: '管理员',
    action: 'TAKE_DOWN',
    reportId: 5,
    reportTitle: '违规启事',
    reason: '包含违规内容',
    createdAt: '2026-10-14T09:00:00',
    ...overrides
  }
}

function page(items: AuditLogView[]): PageResult<AuditLogView> {
  return { items, page: 0, size: 20, total: items.length }
}

describe('AdminAuditLogsPage', () => {
  beforeEach(() => {
    listAuditLogs.mockReset()
  })

  it('渲染审计记录的操作人昵称、动作、目标启事与原因', async () => {
    listAuditLogs.mockResolvedValue(page([auditLog()]))

    const wrapper = mount(AdminAuditLogsPage)
    await flushPromises()

    expect(listAuditLogs).toHaveBeenCalledWith(0, 20)
    expect(wrapper.text()).toContain('管理员')
    expect(wrapper.text()).toContain('下架')
    expect(wrapper.text()).toContain('违规启事')
    expect(wrapper.text()).toContain('包含违规内容')
  })

  it('无审计记录时显示空态', async () => {
    listAuditLogs.mockResolvedValue(page([]))

    const wrapper = mount(AdminAuditLogsPage)
    await flushPromises()

    expect(wrapper.text()).toContain('暂无审计记录')
  })
})
