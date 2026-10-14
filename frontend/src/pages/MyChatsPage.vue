<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listChats } from '@/api/chats'
import type { ChatSessionView, ChatSessionStatus } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import LoadingSkeleton from '@/components/LoadingSkeleton.vue'
import ErrorState from '@/components/ErrorState.vue'

const sessions = ref<ChatSessionView[]>([])
const loading = ref(true)
const failed = ref(false)

const CHAT_STATUS_LABELS: Record<ChatSessionStatus, string> = {
  OPEN: '进行中',
  CLOSED: '已关闭'
}

onMounted(() => {
  void load()
})

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  try {
    sessions.value = await listChats()
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="my-chats">
    <header class="my-chats__header">
      <h1 class="my-chats__title">我的会话</h1>
      <p class="my-chats__subtitle">与认领对方约定线下交还的站内会话。</p>
    </header>

    <div v-if="loading" class="skeleton-list">
      <LoadingSkeleton v-for="n in 3" :key="n" variant="list" :rows="2" />
    </div>
    <ErrorState v-else-if="failed" @retry="load" />
    <EmptyState v-else-if="sessions.length === 0" title="暂无会话" hint="还没有与认领对方建立会话。" />
    <ul v-else class="chat-list">
      <li v-for="session in sessions" :key="session.id" class="chat-card">
        <RouterLink class="chat-card__link" :to="`/me/chats/${session.id}`">
          <span class="chat-card__title">
            {{ session.report ? session.report.title : '关联启事已删除' }}
          </span>
          <span class="chat-badge" :class="`chat-badge--${session.status.toLowerCase()}`">
            {{ CHAT_STATUS_LABELS[session.status] }}
          </span>
        </RouterLink>
        <p class="chat-card__meta">
          <span v-if="session.peerNickname">对方：{{ session.peerNickname }}</span>
          <span class="chat-card__time">{{ session.createdAt }}</span>
        </p>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.my-chats__title {
  margin: 0 0 8px;
  font-size: 22px;
}

.my-chats__subtitle {
  margin: 0 0 16px;
  color: #666;
  font-size: 13px;
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

.my-chats__empty {
  padding: 32px 16px;
  text-align: center;
  color: #666;
  border: 1px dashed #d9dbe0;
  border-radius: 8px;
  background: #fff;
}

.chat-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  gap: 12px;
}

.chat-card {
  padding: 12px;
  border: 1px solid #e4e6eb;
  border-radius: 8px;
  background: #fff;
}

.chat-card__link {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  text-decoration: none;
  color: #1a1a1a;
  font-size: 16px;
  font-weight: 600;
}

.chat-card__link:hover .chat-card__title {
  text-decoration: underline;
}

.chat-badge {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 18px;
  background: #e8f4ea;
  color: #2e7d32;
  font-weight: 400;
}

.chat-badge--closed {
  background: #f2f3f5;
  color: #999;
}

.chat-card__meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin: 6px 0 0;
  color: #666;
  font-size: 13px;
}

.chat-card__time {
  color: #999;
}
</style>
