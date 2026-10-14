import { flushPromises, mount } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import NotificationsPage from './NotificationsPage.vue'
import type { NotificationView } from '@/api/types'

// API 模块被内存 fake 替换：测试只断言「给定 API 返回，用户看到什么、点击后发出什么请求」。
const { listNotifications, markNotificationRead, markAllNotificationsRead } = vi.hoisted(() => ({
  listNotifications: vi.fn(),
  markNotificationRead: vi.fn(),
  markAllNotificationsRead: vi.fn()
}))

vi.mock('@/api/notifications', () => ({
  listNotifications,
  markNotificationRead,
  markAllNotificationsRead
}))

const routes: RouteRecordRaw[] = [
  { path: '/notifications', component: { template: '<div />' } },
  { path: '/reports/:id', component: { template: '<div />' } },
  { path: '/me/chats/:id', component: { template: '<div />' } },
  { path: '/me/claims', component: { template: '<div />' } }
]

function notificationView(overrides: Partial<NotificationView> = {}): NotificationView {
  return {
    id: 1,
    type: 'CLAIM_CREATED',
    targetType: 'CLAIM',
    targetId: 10,
    read: false,
    createdAt: '2026-10-14T09:00:00',
    ...overrides
  }
}

async function mountPage() {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/notifications')
  await router.isReady()
  const wrapper = mount(NotificationsPage, { global: { plugins: [createPinia(), router] } })
  await flushPromises()
  return wrapper
}

describe('NotificationsPage', () => {
  beforeEach(() => {
    listNotifications.mockReset()
    markNotificationRead.mockReset()
    markAllNotificationsRead.mockReset()
  })

  it('渲染通知列表、未读高亮与未读角标', async () => {
    listNotifications.mockResolvedValue({
      items: [
        notificationView({ id: 1, type: 'CLAIM_CREATED', read: false }),
        notificationView({ id: 2, type: 'MESSAGE_RECEIVED', read: true, targetType: 'SESSION', targetId: 5 })
      ],
      unreadCount: 1
    })

    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('收到新的认领申请')
    expect(wrapper.text()).toContain('收到新消息')
    // 未读条目高亮，且未读角标显示未读数。
    expect(wrapper.findAll('.notification-card--unread')).toHaveLength(1)
    expect(wrapper.find('.notifications__badge').text()).toBe('1')
  })

  it('点「全部标记已读」调用接口且未读角标归零', async () => {
    let unread = 2
    const items: NotificationView[] = [
      notificationView({ id: 1, read: false }),
      notificationView({ id: 2, type: 'MESSAGE_RECEIVED', read: false })
    ]
    listNotifications.mockImplementation(() => Promise.resolve({ items, unreadCount: unread }))
    markAllNotificationsRead.mockImplementation(() => {
      unread = 0
      items.forEach((item) => {
        item.read = true
      })
      return Promise.resolve(2)
    })

    const wrapper = await mountPage()
    expect(wrapper.find('.notifications__badge').exists()).toBe(true)

    await wrapper.find('.notifications__read-all').trigger('click')
    await flushPromises()

    expect(markAllNotificationsRead).toHaveBeenCalled()
    // 角标消失，未读高亮清空。
    expect(wrapper.find('.notifications__badge').exists()).toBe(false)
    expect(wrapper.findAll('.notification-card--unread')).toHaveLength(0)
  })

  it('点单条「标记已读」发出对应请求', async () => {
    listNotifications.mockResolvedValue({
      items: [notificationView({ id: 7, read: false })],
      unreadCount: 1
    })
    markNotificationRead.mockResolvedValue(notificationView({ id: 7, read: true }))

    const wrapper = await mountPage()
    await wrapper.find('.notification-card__read').trigger('click')
    await flushPromises()

    expect(markNotificationRead).toHaveBeenCalledWith(7)
  })

  it('无通知时显示中文空态提示', async () => {
    listNotifications.mockResolvedValue({ items: [], unreadCount: 0 })

    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('暂无通知')
  })
})
