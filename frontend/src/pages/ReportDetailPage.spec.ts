import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import ReportDetailPage from './ReportDetailPage.vue'
import { useAuthStore } from '@/stores/auth'
import type { ReportDetail, User } from '@/api/types'

// API 模块被内存 fake 替换：测试只断言「给定 API 返回，用户看到什么、点击后发出什么请求」。
const { getReport, createClaim, createReportFlag } = vi.hoisted(() => ({
  getReport: vi.fn(),
  createClaim: vi.fn(),
  createReportFlag: vi.fn()
}))

vi.mock('@/api/reports', () => ({ getReport }))
vi.mock('@/api/claims', () => ({ createClaim }))
vi.mock('@/api/flags', () => ({ createReportFlag }))

const stub = { template: '<div />' }

const routes: RouteRecordRaw[] = [
  { path: '/reports/:id', component: stub },
  { path: '/login', component: stub }
]

function makeUser(id: number): User {
  return {
    id,
    studentNo: '2021002',
    nickname: '申请人',
    department: null,
    email: null,
    role: 'USER',
    status: 'ACTIVE',
    contactType: null,
    contactValue: null,
    contactPublic: false,
    createdAt: '2026-10-14T09:00:00'
  }
}

function detail(overrides: Partial<ReportDetail> = {}): ReportDetail {
  return {
    id: 1,
    type: 'FOUND',
    title: '捡到的校园卡',
    category: '校园卡',
    location: '图书馆',
    locationDetail: null,
    eventTime: '2026-10-14T08:30:00',
    description: '描述',
    status: 'OPEN',
    pinned: false,
    custody: 'SELF',
    handoverNo: null,
    viewCount: 1,
    publisher: {
      id: 2,
      nickname: '发布者',
      contactType: null,
      contactValue: null,
      contactPublic: false
    },
    images: [],
    rejectReason: null,
    reviewedAt: null,
    createdAt: '2026-10-14T09:00:00',
    ...overrides
  }
}

async function mountDetail(options: {
  authed: boolean
  userId?: number
  report?: Partial<ReportDetail>
}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  getReport.mockResolvedValue(detail(options.report))
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/reports/1')
  await router.isReady()
  const auth = useAuthStore()
  if (options.authed) {
    auth.token = 'token-1'
    auth.user = makeUser(options.userId ?? 99)
  }
  const wrapper = mount(ReportDetailPage, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('ReportDetailPage 认领申请', () => {
  beforeEach(() => {
    getReport.mockReset()
    createClaim.mockReset()
    createReportFlag.mockReset()
    localStorage.clear()
  })

  it('未登录时展示登录引导，点击后带 redirect 跳转登录页', async () => {
    const { wrapper, router } = await mountDetail({ authed: false })

    expect(wrapper.find('.claim__login').exists()).toBe(true)
    expect(wrapper.find('.claim__open').exists()).toBe(false)

    await wrapper.find('.claim__login').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/reports/1')
  })

  it('独有特征为空时提交显示字段级校验且不调用接口', async () => {
    const { wrapper } = await mountDetail({ authed: true, userId: 99 })

    await wrapper.find('.claim__open').trigger('click')
    await wrapper.find('.claim-form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('请填写独有特征说明')
    expect(createClaim).not.toHaveBeenCalled()
  })

  it('提交成功后显示状态提示并发出正确请求', async () => {
    createClaim.mockResolvedValue({})
    const { wrapper } = await mountDetail({ authed: true, userId: 99 })

    await wrapper.find('.claim__open').trigger('click')
    await wrapper.find('.claim-form__feature').setValue('校园卡卡号后四位是 1234')
    await wrapper.find('.claim-form').trigger('submit')
    await flushPromises()

    expect(createClaim).toHaveBeenCalledWith(
      '1',
      expect.objectContaining({ featureDesc: '校园卡卡号后四位是 1234' })
    )
    expect(wrapper.text()).toContain('认领申请已提交，等待发布者处理')
  })

  it('发布者本人看不到申请入口', async () => {
    const { wrapper } = await mountDetail({ authed: true, userId: 2 })

    expect(wrapper.find('.claim__open').exists()).toBe(false)
    expect(wrapper.text()).toContain('自己发布的启事')
  })

  it('已结束的启事不展示申请入口并提示已结束', async () => {
    const { wrapper } = await mountDetail({
      authed: true,
      userId: 99,
      report: { status: 'RETURNED' }
    })

    expect(wrapper.find('.claim__open').exists()).toBe(false)
    expect(wrapper.text()).toContain('该启事已结束')
  })
})

describe('ReportDetailPage 举报', () => {
  beforeEach(() => {
    getReport.mockReset()
    createClaim.mockReset()
    createReportFlag.mockReset()
    localStorage.clear()
  })

  it('点举报打开理由弹窗，选理由后提交成功并提示', async () => {
    createReportFlag.mockResolvedValue({})
    const { wrapper } = await mountDetail({ authed: true, userId: 99 })

    await wrapper.find('.flag__open').trigger('click')
    await flushPromises()
    expect(wrapper.find('.flag-modal').exists()).toBe(true)

    await wrapper.find('input[value="ABUSE"]').setValue()
    await wrapper.find('.flag-modal__detail').setValue('包含辱骂内容')
    await wrapper.find('.flag-modal__submit').trigger('click')
    await flushPromises()

    expect(createReportFlag).toHaveBeenCalledWith('1', { reason: 'ABUSE', detail: '包含辱骂内容' })
    expect(wrapper.text()).toContain('举报已提交')
    expect(wrapper.find('.flag-modal').exists()).toBe(false)
  })

  it('未登录时举报入口引导登录', async () => {
    const { wrapper } = await mountDetail({ authed: false })

    expect(wrapper.find('.flag__open').exists()).toBe(false)
    expect(wrapper.find('.flag__login').exists()).toBe(true)
  })
})

describe('ReportDetailPage 加载与错误态', () => {
  beforeEach(() => {
    getReport.mockReset()
    createClaim.mockReset()
    createReportFlag.mockReset()
    localStorage.clear()
  })

  async function mountUnset() {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = createRouter({ history: createMemoryHistory(), routes })
    await router.push('/reports/1')
    await router.isReady()
    const wrapper = mount(ReportDetailPage, { global: { plugins: [pinia, router] } })
    await flushPromises()
    return wrapper
  }

  it('加载中渲染骨架屏而不是空白', async () => {
    getReport.mockReturnValue(new Promise(() => {}))

    const wrapper = await mountUnset()

    expect(wrapper.find('.loading-skeleton').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('加载中…')
  })

  it('加载失败显示错误态，点重试重新拉取详情', async () => {
    getReport.mockRejectedValueOnce(new Error('boom')).mockResolvedValueOnce(detail())

    const wrapper = await mountUnset()

    expect(wrapper.find('.error-state').exists()).toBe(true)
    expect(wrapper.text()).toContain('加载失败')

    await wrapper.find('.error-state__retry').trigger('click')
    await flushPromises()

    expect(getReport).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('捡到的校园卡')
  })
})