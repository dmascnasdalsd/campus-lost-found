<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError } from '@/api/client'
import { listMessages, sendMessage } from '@/api/chats'
import type { MessageView } from '@/api/types'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const auth = useAuthStore()

const messages = ref<MessageView[]>([])
const loading = ref(true)
const failed = ref(false)
const draft = ref('')
const sending = ref(false)
const sendError = ref('')

onMounted(() => {
  void load()
})

async function load(): Promise<void> {
  loading.value = true
  failed.value = false
  try {
    messages.value = await listMessages(String(route.params.id))
  } catch {
    failed.value = true
  } finally {
    loading.value = false
  }
}

/** 当前用户是否为该消息发送者（区分自己 / 对方气泡）。 */
function isSelf(message: MessageView): boolean {
  return auth.user?.id != null && auth.user.id === message.senderId
}

async function onSend(): Promise<void> {
  const body = draft.value.trim()
  if (body === '' || sending.value) {
    return
  }
  sending.value = true
  sendError.value = ''
  try {
    const sent = await sendMessage(String(route.params.id), { body })
    messages.value = [...messages.value, sent]
    draft.value = ''
  } catch (error) {
    sendError.value = error instanceof ApiError ? error.message : '发送失败，请稍后重试'
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <section class="chat">
    <p v-if="loading" class="state">加载中…</p>
    <p v-else-if="failed" class="state state--error">会话不存在或加载失败。</p>

    <template v-else>
      <ul class="chat__messages">
        <li
          v-for="message in messages"
          :key="message.id"
          class="message"
          :class="isSelf(message) ? 'message--self' : 'message--peer'"
        >
          <p class="message__sender">{{ isSelf(message) ? '我' : (message.senderNickname ?? '对方') }}</p>
          <p class="message__body">{{ message.body }}</p>
        </li>
      </ul>
      <p v-if="messages.length === 0" class="chat__empty">还没有消息，来打个招呼吧。</p>

      <form class="chat__composer" novalidate @submit.prevent="onSend">
        <input
          v-model="draft"
          class="chat__input"
          type="text"
          placeholder="输入消息…"
        />
        <button class="chat__send" type="button" :disabled="sending" @click="onSend">
          {{ sending ? '发送中…' : '发送' }}
        </button>
      </form>
      <p v-if="sendError" class="chat__error">{{ sendError }}</p>
    </template>
  </section>
</template>

<style scoped>
.state {
  color: #666;
}

.state--error {
  color: #c0392b;
}

.chat__messages {
  list-style: none;
  margin: 0 0 16px;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.message {
  max-width: 70%;
  padding: 8px 12px;
  border-radius: 8px;
  background: #f2f3f5;
}

.message--self {
  align-self: flex-end;
  background: #e8f4ea;
}

.message--peer {
  align-self: flex-start;
}

.message__sender {
  margin: 0 0 2px;
  color: #888;
  font-size: 12px;
}

.message__body {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.6;
}

.chat__empty {
  color: #666;
}

.chat__composer {
  display: flex;
  gap: 8px;
}

.chat__input {
  flex: 1 1 auto;
  padding: 8px 10px;
  border: 1px solid #d9dbe0;
  border-radius: 6px;
  font-size: 14px;
  font-family: inherit;
}

.chat__send {
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  background: #2e7d32;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
}

.chat__error {
  margin: 8px 0 0;
  color: #c0392b;
  font-size: 13px;
}
</style>
