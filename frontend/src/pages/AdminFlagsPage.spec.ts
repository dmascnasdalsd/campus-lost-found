import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import AdminFlagsPage from './AdminFlagsPage.vue'
import type { FlagView, PageResult } from '@/api/types'

// API 模块被内存 fake 替换：测试只断言「给定 API 返回，用户看到什么、点击后发出什么请求」。
const { listAdminFlags, resolveFlag } = vi.hoisted(() => ({
  listAdminFlags: vi.fn(),
  resolveFlag: vi.fn()
}))

vi.mock('@/api/admin', () => ({ listAdminFlags, resolveFlag }))

const routes: RouteRecordRaw[] = [
  { path: '/admin/flags', component: { template: '<div />' } },
  { path: '/reports/:id', component: { template: '<div />' } }
]

function flagView(overrides: Partial<FlagView> = {}): FlagView {
  return {
    id: 1,
    targetType: 'REPORT',
    targetId: 10,
    targetSummary: '捡到的校园卡',
    reason: 'FAKE',
    detail: null,
    status: 'PENDING',
    handleResult: null,
    reporterId: 5,
    reporterNickname: '举报人',
    handledBy: null,
    handledAt: null,
    createdAt: '2026-10-14T09:00:00',
    ...overrides
  }
}

function page(items: FlagView[]): PageResult<FlagView> {
  return { items, page: 0, size: 20, total: items.length }
}

async function mountPage() {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/admin/flags')
  await router.isReady()
  const wrapper = mount(AdminFlagsPage, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

describe('AdminFlagsPage', () => {
  beforeEach(() => {
    listAdminFlags.mockReset()
    resolveFlag.mockReset()
  })

  it('默认按待处理筛选并渲染内容摘要、理由、举报人与状态', async () => {
    listAdminFlags.mockResolvedValue(page([flagView()]))

    const wrapper = await mountPage()

    expect(listAdminFlags).toHaveBeenCalledWith('PENDING')
    expect(wrapper.text()).toContain('捡到的校园卡')
    expect(wrapper.text()).toContain('虚假信息')
    expect(wrapper.text()).toContain('举报人')
    expect(wrapper.text()).toContain('待处理')
  })

  it('待处理举报显示三种处置按钮，已处置举报不显示', async () => {
    listAdminFlags.mockResolvedValue(
      page([
        flagView({ id: 1, status: 'PENDING' }),
        flagView({ id: 2, status: 'RESOLVED', handleResult: 'TAKE_DOWN' })
      ])
    )

    const wrapper = await mountPage()

    expect(wrapper.findAll('.flag-card__dismiss')).toHaveLength(1)
    expect(wrapper.findAll('.flag-card__takedown')).toHaveLength(1)
    expect(wrapper.findAll('.flag-card__disable')).toHaveLength(1)
    expect(wrapper.text()).toContain('已处置')
  })

  it('点下架内容调用接口并重新加载列表', async () => {
    listAdminFlags.mockResolvedValue(page([flagView({ id: 7 })]))
    resolveFlag.mockResolvedValue(flagView({ id: 7, status: 'RESOLVED', handleResult: 'TAKE_DOWN' }))

    const wrapper = await mountPage()
    await wrapper.find('.flag-card__takedown').trigger('click')
    await flushPromises()

    expect(resolveFlag).toHaveBeenCalledWith(7, 'TAKE_DOWN')
    expect(listAdminFlags).toHaveBeenCalledTimes(2)
  })

  it('切换到全部按无状态参数请求', async () => {
    listAdminFlags.mockResolvedValue(page([flagView()]))

    const wrapper = await mountPage()
    await wrapper.find('.filter-all').trigger('click')
    await flushPromises()

    expect(listAdminFlags).toHaveBeenLastCalledWith(undefined)
  })
})
