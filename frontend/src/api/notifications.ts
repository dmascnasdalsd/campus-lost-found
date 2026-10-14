import { request } from './client'
import type { NotificationListView, NotificationView } from './types'

/** 我的通知列表与未读数。 */
export function listNotifications(): Promise<NotificationListView> {
  return request<NotificationListView>('/notifications')
}

/** 标记单条通知已读。 */
export function markNotificationRead(id: number | string): Promise<NotificationView> {
  return request<NotificationView>(`/notifications/${id}/read`, { method: 'POST' })
}

/** 一键全部标记已读，返回本次标记数量。 */
export function markAllNotificationsRead(): Promise<number> {
  return request<number>('/notifications/read-all', { method: 'POST' })
}
