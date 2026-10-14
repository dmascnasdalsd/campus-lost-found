<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useNotificationStore } from '@/stores/notification'
import type { NotificationTargetType, NotificationType, NotificationView } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import LoadingSkeleton from '@/components/LoadingSkeleton.vue'
import ErrorState from '@/components/ErrorState.vue'

const notification = useNotificationStore()

const loading = ref(true)
const failed = ref(false)
const actionError = ref('')

const NOTIFICATION_TYPE_LABELS: Record<NotificationType, string> = {
  CLAIM_CREATED: '收到新的认领申请',
  CLAIM_ACCEPTED: '你的认领申请已被接受',
  CLAIM_REJECTED: '你的认领申请被驳回',
  CLAIM_CLOSED: '你的认领申请已自动关闭',
  MESSAGE_RECEIVED: '收到新消息',
  REPORT_TAKE_DOWN: '你的启事被下架',
  REPORT_REJECTED: '你的启事被驳回'
}

const unreadCount = computed(() => notification.unreadCount)
const items = computed(() => notification.items)

onMounted(() => {
  void load()
})

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  try {
    await notification.load()
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

/** 关联对象的跳转地址；无关联对象时返回 null。 */
function targetLink(view: NotificationView): string | null {
  if (view.targetId == null || view.targetType == null) {
    return null
  }
  const byType: Record<NotificationTargetType, string> = {
    REPORT: `/reports/${view.targetId}`,
    CLAIM: '/me/claims',
    SESSION: `/me/chats/${view.targetId}`
  }
  return byType[view.targetType]
}

/** 全部标记已读。 */
async function onMarkAllRead(): Promise<void> {
  actionError.value = ''
  try {
    await notification.markAllRead()
  } catch {
    actionError.value = '操作失败，请稍后重试'
  }
}

/** 标记单条已读。 */
async function onMarkRead(view: NotificationView): Promise<void> {
  actionError.value = ''
  try {
    await notification.markRead(view.id)
  } catch {
    actionError.value = '操作失败，请稍后重试'
  }
}
</script>

<template>
  <section class="notifications">
    <header class="notifications__header">
      <h1 class="notifications__title">
        通知中心
        <span v-if="unreadCount > 0" class="notifications__badge">{{ unreadCount }}</span>
      </h1>
      <button
        class="notifications__read-all"
        type="button"
        :disabled="unreadCount === 0"
        @click="onMarkAllRead"
      >
        全部标记已读
      </button>
    </header>
    <p class="notifications__subtitle">认领申请与会话消息的相关动态都会在这里提醒你。</p>

    <p v-if="actionError" class="notifications__error">{{ actionError }}</p>

    <div v-if="loading" class="skeleton-list">
      <LoadingSkeleton v-for="n in 3" :key="n" variant="list" :rows="2" />
    </div>
    <ErrorState v-else-if="failed" @retry="load" />
    <EmptyState
      v-else-if="items.length === 0"
      title="暂无通知"
      hint="认领申请与会话消息的动态都会在这里提醒你。"
    />
    <ul v-else class="notification-list">
      <li
        v-for="view in items"
        :key="view.id"
        class="notification-card"
        :class="{ 'notification-card--unread': !view.read }"
      >
        <div class="notification-card__head">
          <RouterLink
            v-if="targetLink(view)"
            class="notification-card__title"
            :to="targetLink(view)!"
          >
            {{ NOTIFICATION_TYPE_LABELS[view.type] }}
          </RouterLink>
          <span v-else class="notification-card__title">{{ NOTIFICATION_TYPE_LABELS[view.type] }}</span>
          <span v-if="!view.read" class="notification-card__dot">未读</span>
        </div>
        <p class="notification-card__meta">
          <span class="notification-card__time">{{ view.createdAt }}</span>
          <button
            v-if="!view.read"
            class="notification-card__read"
            type="button"
            @click="onMarkRead(view)"
          >
            标记已读
          </button>
        </p>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.notifications__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.notifications__title {
  margin: 0;
  font-size: 22px;
}

.notifications__badge {
  display: inline-block;
  margin-left: 8px;
  padding: 0 8px;
  border-radius: 10px;
  background: #c0392b;
  color: #fff;
  font-size: 12px;
  line-height: 20px;
  vertical-align: middle;
}

.notifications__subtitle {
  margin: 8px 0 16px;
  color: #666;
  font-size: 13px;
}

.notifications__read-all {
  padding: 6px 14px;
  border: 1px solid #2e7d32;
  border-radius: 6px;
  background: #fff;
  color: #2e7d32;
  font-size: 13px;
  cursor: pointer;
}

.notifications__read-all:disabled {
  border-color: #d9dbe0;
  color: #999;
  cursor: default;
}

.notifications__error {
  margin: 0 0 12px;
  color: #c0392b;
  font-size: 13px;
}

.notifications__empty {
  padding: 32px 16px;
  text-align: center;
  color: #666;
  border: 1px dashed #d9dbe0;
  border-radius: 8px;
  background: #fff;
}

.state {
  color: #666;
}

.state--error {
  color: #c0392b;
}

.skeleton-list {
  display: grid;
  gap: 12px;
}

.notification-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 12px;
}

.notification-card {
  padding: 12px;
  border: 1px solid #e4e6eb;
  border-radius: 8px;
  background: #fff;
}

.notification-card--unread {
  border-color: #f0c9c4;
  background: #fff8f7;
}

.notification-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.notification-card__title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
  text-decoration: none;
}

.notification-card__title:hover {
  text-decoration: underline;
}

.notification-card__dot {
  padding: 1px 6px;
  border-radius: 4px;
  background: #fdecea;
  color: #c0392b;
  font-size: 12px;
  line-height: 18px;
}

.notification-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 6px 0 0;
  color: #999;
  font-size: 13px;
}

.notification-card__read {
  padding: 4px 10px;
  border: 1px solid #d9dbe0;
  border-radius: 6px;
  background: #fff;
  color: #566272;
  font-size: 12px;
  cursor: pointer;
}
</style>
