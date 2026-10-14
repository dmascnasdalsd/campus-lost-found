import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useNotificationStore } from './notification'
import type { NotificationView } from '@/api/types'

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

describe('useNotificationStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    listNotifications.mockReset()
    markNotificationRead.mockReset()
    markAllNotificationsRead.mockReset()
  })

  it('fetchUnread 设置未读数', async () => {
    listNotifications.mockResolvedValue({ items: [], unreadCount: 3 })

    const store = useNotificationStore()
    await store.fetchUnread()

    expect(store.unreadCount).toBe(3)
  })

  it('fetchUnread 失败时静默不抛出', async () => {
    listNotifications.mockRejectedValue(new Error('network'))

    const store = useNotificationStore()
    await expect(store.fetchUnread()).resolves.toBeUndefined()

    expect(store.unreadCount).toBe(0)
  })

  it('load 设置列表与未读数', async () => {
    listNotifications.mockResolvedValue({ items: [notificationView()], unreadCount: 1 })

    const store = useNotificationStore()
    await store.load()

    expect(store.items).toHaveLength(1)
    expect(store.unreadCount).toBe(1)
  })

  it('markAllRead 后未读数归零', async () => {
    let unread = 2
    listNotifications.mockImplementation(() => Promise.resolve({ items: [], unreadCount: unread }))
    markAllNotificationsRead.mockImplementation(() => {
      unread = 0
      return Promise.resolve(2)
    })

    const store = useNotificationStore()
    await store.fetchUnread()
    expect(store.unreadCount).toBe(2)

    await store.markAllRead()

    expect(markAllNotificationsRead).toHaveBeenCalled()
    expect(store.unreadCount).toBe(0)
  })
})
