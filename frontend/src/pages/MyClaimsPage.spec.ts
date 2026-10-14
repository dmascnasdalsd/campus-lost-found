import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import MyClaimsPage from './MyClaimsPage.vue'
import type { ClaimView } from '@/api/types'

// API 模块被内存 fake 替换：测试只断言「给定 API 返回，用户看到什么、点击后发出什么请求」。
const { listClaims, withdrawClaim, acceptClaim, rejectClaim } = vi.hoisted(() => ({
  listClaims: vi.fn(),
  withdrawClaim: vi.fn(),
  acceptClaim: vi.fn(),
  rejectClaim: vi.fn()
}))

vi.mock('@/api/claims', () => ({ listClaims, withdrawClaim, acceptClaim, rejectClaim }))

const routes: RouteRecordRaw[] = [
  { path: '/me/claims', component: { template: '<div />' } },
  { path: '/reports/:id', component: { template: '<div />' } }
]

function claimView(overrides: Partial<ClaimView> = {}): ClaimView {
  return {
    id: 1,
    direction: 'APPLY_CLAIM',
    status: 'PENDING',
    featureDesc: '校园卡卡号后四位是 1234',
    expectTime: null,
    expectPlace: null,
    rejectReason: null,
    handledAt: null,
    createdAt: '2026-10-14T09:00:00',
    report: {
      id: 10,
      type: 'FOUND',
      title: '捡到的校园卡',
      category: '校园卡',
      location: '图书馆',
      locationDetail: null,
      eventTime: null,
      status: 'OPEN',
      pinned: false,
      custody: 'SELF',
      thumbnailPath: null,
      viewCount: 0,
      createdAt: '2026-10-14T09:00:00'
    },
    applicantId: 3,
    applicantNickname: '申请人',
    ...overrides
  }
}

async function mountPage() {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/me/claims')
  await router.isReady()
  const wrapper = mount(MyClaimsPage, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

describe('MyClaimsPage', () => {
  beforeEach(() => {
    listClaims.mockReset()
    withdrawClaim.mockReset()
    acceptClaim.mockReset()
    rejectClaim.mockReset()
  })

  it('默认展示我发起的申请与关联启事摘要，PENDING 显示撤回按钮', async () => {
    listClaims.mockResolvedValue([claimView()])

    const wrapper = await mountPage()

    expect(listClaims).toHaveBeenCalledWith('applied')
    expect(wrapper.text()).toContain('捡到的校园卡')
    expect(wrapper.text()).toContain('待处理')
    expect(wrapper.find('.claim-card__withdraw').exists()).toBe(true)
  })

  it('非 PENDING 的我发起的申请不显示撤回按钮', async () => {
    listClaims.mockResolvedValue([claimView({ status: 'REJECTED', rejectReason: '特征不符' })])

    const wrapper = await mountPage()

    expect(wrapper.find('.claim-card__withdraw').exists()).toBe(false)
    expect(wrapper.text()).toContain('已驳回')
    expect(wrapper.text()).toContain('特征不符')
  })

  it('切换到「我收到的」按 received 请求并展示申请人昵称', async () => {
    listClaims.mockImplementation((role: string) =>
      Promise.resolve(role === 'received' ? [claimView({ status: 'PENDING' })] : [])
    )

    const wrapper = await mountPage()
    await wrapper.find('.tab-received').trigger('click')
    await flushPromises()

    expect(listClaims).toHaveBeenLastCalledWith('received')
    expect(wrapper.text()).toContain('申请人：申请人')
    // 我收到的 tab 不提供撤回。
    expect(wrapper.find('.claim-card__withdraw').exists()).toBe(false)
  })

  it('确认后撤回发出请求并重新加载列表', async () => {
    listClaims.mockResolvedValue([claimView()])
    withdrawClaim.mockResolvedValue({})
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const wrapper = await mountPage()
    await wrapper.find('.claim-card__withdraw').trigger('click')
    await flushPromises()

    expect(withdrawClaim).toHaveBeenCalledWith(1)
    expect(listClaims).toHaveBeenCalledTimes(2)
  })

  it('取消确认时不发出撤回请求', async () => {
    listClaims.mockResolvedValue([claimView()])
    vi.spyOn(window, 'confirm').mockReturnValue(false)

    const wrapper = await mountPage()
    await wrapper.find('.claim-card__withdraw').trigger('click')
    await flushPromises()

    expect(withdrawClaim).not.toHaveBeenCalled()
  })

  it('我收到的 PENDING 申请显示接受与拒绝按钮', async () => {
    listClaims.mockImplementation((role: string) =>
      Promise.resolve(role === 'received' ? [claimView({ status: 'PENDING' })] : [])
    )

    const wrapper = await mountPage()
    await wrapper.find('.tab-received').trigger('click')
    await flushPromises()

    expect(wrapper.find('.claim-card__accept').exists()).toBe(true)
    expect(wrapper.find('.claim-card__reject').exists()).toBe(true)
    // 我收到的 tab 不提供撤回。
    expect(wrapper.find('.claim-card__withdraw').exists()).toBe(false)
  })

  it('确认后接受发出请求并重新加载列表', async () => {
    listClaims.mockImplementation((role: string) =>
      Promise.resolve(role === 'received' ? [claimView({ status: 'PENDING' })] : [])
    )
    acceptClaim.mockResolvedValue({})
    vi.spyOn(window, 'confirm').mockReturnValue(true)

    const wrapper = await mountPage()
    await wrapper.find('.tab-received').trigger('click')
    await flushPromises()
    await wrapper.find('.claim-card__accept').trigger('click')
    await flushPromises()

    expect(acceptClaim).toHaveBeenCalledWith(1)
    // 初次 applied + 切 tab received + 接受后重新加载 received，共 3 次。
    expect(listClaims).toHaveBeenCalledTimes(3)
  })

  it('点拒绝打开弹窗并提交驳回原因', async () => {
    listClaims.mockImplementation((role: string) =>
      Promise.resolve(role === 'received' ? [claimView({ status: 'PENDING' })] : [])
    )
    rejectClaim.mockResolvedValue({})

    const wrapper = await mountPage()
    await wrapper.find('.tab-received').trigger('click')
    await flushPromises()

    expect(wrapper.find('.reject-modal').exists()).toBe(false)

    await wrapper.find('.claim-card__reject').trigger('click')
    await flushPromises()
    expect(wrapper.find('.reject-modal').exists()).toBe(true)

    await wrapper.find('.reject-modal__reason').setValue('特征与实物不符')
    await wrapper.find('.reject-modal__submit').trigger('click')
    await flushPromises()

    expect(rejectClaim).toHaveBeenCalledWith(1, { rejectReason: '特征与实物不符' })
    expect(wrapper.find('.reject-modal').exists()).toBe(false)
  })

  it('驳回原因为空时不发出请求并提示', async () => {
    listClaims.mockImplementation((role: string) =>
      Promise.resolve(role === 'received' ? [claimView({ status: 'PENDING' })] : [])
    )

    const wrapper = await mountPage()
    await wrapper.find('.tab-received').trigger('click')
    await flushPromises()
    await wrapper.find('.claim-card__reject').trigger('click')
    await wrapper.find('.reject-modal__submit').trigger('click')
    await flushPromises()

    expect(rejectClaim).not.toHaveBeenCalled()
    expect(wrapper.find('.reject-modal__error').text()).toContain('请填写驳回原因')
  })
})
