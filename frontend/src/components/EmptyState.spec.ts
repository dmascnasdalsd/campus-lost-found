import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import EmptyState from './EmptyState.vue'

const routes: RouteRecordRaw[] = [
  { path: '/', component: { template: '<div />' } },
  { path: '/reports/new', component: { template: '<div />' } }
]

async function mountState(props: Record<string, string> = {}) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/')
  await router.isReady()
  return mount(EmptyState, { props, global: { plugins: [router] } })
}

describe('EmptyState', () => {
  it('无 props 时显示默认中文文案且不渲染跳转链接', async () => {
    const wrapper = await mountState()

    expect(wrapper.text()).toContain('还没有启事')
    expect(wrapper.find('.empty-state__action').exists()).toBe(false)
  })

  it('提供 actionTo 时渲染指向目标的 CTA 链接', async () => {
    const wrapper = await mountState({
      title: '没有找到？',
      hint: '发布一条失物启事',
      actionText: '发布一条失物启事',
      actionTo: '/reports/new'
    })

    const action = wrapper.find('.empty-state__action')
    expect(action.exists()).toBe(true)
    expect(action.text()).toContain('发布一条失物启事')
    expect(action.attributes('href')).toBe('/reports/new')
  })
})
