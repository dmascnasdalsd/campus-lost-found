import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import ReportsNewPage from './ReportsNewPage.vue'

// API client 模块被内存 fake 替换：断言「给定输入，用户看到什么、发出什么请求」。
const { createReport, uploadImages, getReport, listReports } = vi.hoisted(() => ({
  createReport: vi.fn(),
  uploadImages: vi.fn(),
  getReport: vi.fn(),
  listReports: vi.fn()
}))

vi.mock('@/api/reports', () => ({ createReport, uploadImages, getReport, listReports }))

function mountPage() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/reports/:id', component: { template: '<div />' } }
    ]
  })
  return mount(ReportsNewPage, { global: { plugins: [router] } })
}

describe('ReportsNewPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // jsdom 未实现 createObjectURL，测试里打桩。
    Object.defineProperty(URL, 'createObjectURL', {
      value: vi.fn(() => 'blob:preview'),
      writable: true,
      configurable: true
    })
    Object.defineProperty(URL, 'revokeObjectURL', {
      value: vi.fn(),
      writable: true,
      configurable: true
    })
  })

  it('未填写必填项时给出字段级中文提示且不发起请求', async () => {
    const wrapper = mountPage()

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('请填写物品名称')
    expect(wrapper.text()).toContain('请选择分类')
    expect(wrapper.text()).toContain('请选择地点')
    expect(wrapper.text()).toContain('请至少选择 1 张图片')
    expect(createReport).not.toHaveBeenCalled()
    expect(uploadImages).not.toHaveBeenCalled()
  })

  it('选择图片后出现预览缩略图', async () => {
    const wrapper = mountPage()
    const input = wrapper.find('input[type="file"]')
    const file = new File(['pixels'], 'campus-card.png', { type: 'image/png' })

    Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
    await input.trigger('change')
    await flushPromises()

    const items = wrapper.findAll('.preview-item')
    expect(items).toHaveLength(1)
    expect(wrapper.find('.preview-item .preview-item__img').attributes('src')).toBe('blob:preview')
    expect(wrapper.text()).not.toContain('请至少选择 1 张图片')
  })
})
