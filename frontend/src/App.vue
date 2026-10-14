<script setup lang="ts">
import { onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'

const auth = useAuthStore()
const notification = useNotificationStore()
const router = useRouter()

/** 未读角标轮询：已登录时每 30 秒刷新一次未读数。 */
const POLL_INTERVAL_MS = 30_000
let pollTimer: ReturnType<typeof setInterval> | null = null

function startPolling(): void {
  void notification.fetchUnread()
  if (pollTimer === null) {
    pollTimer = setInterval(() => {
      void notification.fetchUnread()
    }, POLL_INTERVAL_MS)
  }
}

function stopPolling(): void {
  if (pollTimer !== null) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

// token 变化（含刷新后从 localStorage 恢复）时刷新当前用户信息并启停角标轮询。
onMounted(() => {
  if (auth.isAuthenticated) {
    void auth.fetchMe()
    startPolling()
  }
})

watch(
  () => auth.token,
  (token) => {
    if (token) {
      void auth.fetchMe()
      startPolling()
    } else {
      stopPolling()
    }
  }
)

// 组件卸载时停止轮询，避免定时器泄漏。
onUnmounted(stopPolling)

async function onLogout(): Promise<void> {
  await auth.logout()
  await router.push('/')
}
</script>

<template>
  <div class="app">
    <nav class="app__nav">
      <RouterLink class="app__brand" to="/">校园失物招领</RouterLink>
      <div class="app__actions">
        <template v-if="auth.isAuthenticated">
          <RouterLink class="app__link" to="/me/reports">我的发布</RouterLink>
          <RouterLink class="app__link" to="/me/claims">我的认领</RouterLink>
          <RouterLink class="app__link" to="/me/chats">我的会话</RouterLink>
          <RouterLink class="app__link app__notification" to="/notifications">
            通知
            <span v-if="notification.unreadCount > 0" class="app__notification-badge">
              {{ notification.unreadCount }}
            </span>
          </RouterLink>
          <RouterLink v-if="auth.user?.role === 'ADMIN'" class="app__link" to="/admin/reports">
            管理后台
          </RouterLink>
          <RouterLink v-if="auth.user?.role === 'ADMIN'" class="app__link" to="/admin/flags">
            举报处理
          </RouterLink>
          <RouterLink v-if="auth.user?.role === 'ADMIN'" class="app__link" to="/admin/stats">
            统计看板
          </RouterLink>
          <span class="app__user">{{ auth.user?.nickname ?? '已登录' }}</span>
          <button class="app__link" type="button" @click="onLogout">退出登录</button>
        </template>
        <template v-else>
          <RouterLink class="app__link" to="/login">登录</RouterLink>
          <RouterLink class="app__link" to="/register">注册</RouterLink>
        </template>
      </div>
    </nav>
    <main class="app__main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.app__notification-badge {
  display: inline-block;
  margin-left: 4px;
  padding: 0 6px;
  border-radius: 9px;
  background: #c0392b;
  color: #fff;
  font-size: 12px;
  line-height: 18px;
}
</style>
