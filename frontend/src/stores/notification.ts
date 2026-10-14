import { ref } from 'vue'
import { defineStore } from 'pinia'
import * as notificationApi from '@/api/notifications'
import type { NotificationView } from '@/api/types'

/**
 * 通知中心状态：未读角标数量与通知列表。
 * fetchUnread 可单独调用且失败时静默，供导航栏轮询使用，不打扰用户。
 */
export const useNotificationStore = defineStore('notification', () => {
  const unreadCount = ref(0)
  const items = ref<NotificationView[]>([])

  /** 仅刷新未读数（导航栏轮询）；失败静默。 */
  async function fetchUnread(): Promise<void> {
    try {
      const result = await notificationApi.listNotifications()
      unreadCount.value = result.unreadCount
    } catch {
      // 静默失败：轮询不应把错误抛到界面。
    }
  }

  /** 加载通知列表与未读数。 */
  async function load(): Promise<void> {
    const result = await notificationApi.listNotifications()
    items.value = result.items
    unreadCount.value = result.unreadCount
  }

  /** 全部标记已读：调用接口后未读数归零并刷新列表。 */
  async function markAllRead(): Promise<void> {
    await notificationApi.markAllNotificationsRead()
    unreadCount.value = 0
    await load()
  }

  /** 标记单条已读并刷新列表与未读数。 */
  async function markRead(id: number | string): Promise<void> {
    await notificationApi.markNotificationRead(id)
    await load()
  }

  return { unreadCount, items, fetchUnread, load, markAllRead, markRead }
})
