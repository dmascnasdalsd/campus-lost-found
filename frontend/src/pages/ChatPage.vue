<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ApiError } from '@/api/client'
import { listMessages, sendMessage } from '@/api/chats'
import { createMessageFlag } from '@/api/flags'
import type { FlagReason, MessageView } from '@/api/types'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const auth = useAuthStore()

const messages = ref<MessageView[]>([])
const loading = ref(true)
const failed = ref(false)
const draft = ref('')
const sending = ref(false)
const sendError = ref('')

/** 举报理由选项：与后端 FlagReason 枚举一致。 */
const REPORT_REASONS: { value: FlagReason; label: string }[] = [
  { value: 'FAKE', label: '虚假信息' },
  { value: 'AD', label: '广告' },
  { value: 'ABUSE', label: '辱骂' },
  { value: 'OTHER', label: '其他' }
]

const flagTarget = ref<MessageView | null>(null)
const flagReason = ref<FlagReason>('FAKE')
const flagDetail = ref('')
const flagSubmitting = ref(false)
const flagError = ref('')
const flagSuccess = ref('')

/** 打开举报弹窗（针对对方发送的消息）。 */
function openFlag(message: MessageView): void {
  flagTarget.value = message
  flagReason.value = 'FAKE'
  flagDetail.value = ''
  flagError.value = ''
}

function closeFlag(): void {
  flagTarget.value = null
  flagReason.value = 'FAKE'
  flagDetail.value = ''
  flagError.value = ''
}

/** 提交举报：成功后关闭弹窗并提示。 */
async function submitFlag(): Promise<void> {
  if (!flagTarget.value) {
    return
  }
  flagSubmitting.value = true
  flagError.value = ''
  try {
    await createMessageFlag(String(flagTarget.value.id), {
      reason: flagReason.value,
      detail: flagDetail.value.trim() === '' ? undefined : flagDetail.value.trim()
    })
    closeFlag()
    flagSuccess.value = '举报已提交'
  } catch (error) {
    flagError.value = error instanceof ApiError ? error.message : '提交失败，请稍后重试'
  } finally {
    flagSubmitting.value = false
  }
}

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
      <p v-if="flagSuccess" class="chat__success">{{ flagSuccess }}</p>
      <ul class="chat__messages">
        <li
          v-for="message in messages"
          :key="message.id"
          class="message"
          :class="isSelf(message) ? 'message--self' : 'message--peer'"
        >
          <p class="message__sender">
            {{ isSelf(message) ? '我' : (message.senderNickname ?? '对方') }}
            <button
              v-if="!isSelf(message)"
              class="message__flag"
              type="button"
              @click="openFlag(message)"
            >
              举报
            </button>
          </p>
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

      <div v-if="flagTarget" class="flag-modal" role="dialog">
        <div class="flag-modal__box">
          <h2 class="flag-modal__title">举报消息</h2>
          <fieldset class="flag-modal__reasons">
            <legend class="flag-modal__legend">举报理由</legend>
            <label
              v-for="option in REPORT_REASONS"
              :key="option.value"
              class="flag-modal__option"
            >
              <input
                v-model="flagReason"
                type="radio"
                name="flag-reason"
                :value="option.value"
                :class="`flag-modal__reason-${option.value}`"
              />
              {{ option.label }}
            </label>
          </fieldset>
          <textarea
            v-model="flagDetail"
            class="flag-modal__detail"
            rows="2"
            placeholder="补充说明（可选）"
          ></textarea>
          <p v-if="flagError" class="flag-modal__error">{{ flagError }}</p>
          <div class="flag-modal__actions">
            <button
              class="flag-modal__submit"
              type="button"
              :disabled="flagSubmitting"
              @click="submitFlag"
            >
              {{ flagSubmitting ? '提交中…' : '提交举报' }}
            </button>
            <button class="flag-modal__cancel" type="button" @click="closeFlag">取消</button>
          </div>
        </div>
      </div>
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

.chat__success {
  margin: 0 0 8px;
  color: #2e7d32;
  font-size: 13px;
}

.message__flag {
  margin-left: 6px;
  padding: 0 6px;
  border: 1px solid #c0392b;
  border-radius: 4px;
  background: #fff;
  color: #c0392b;
  font-size: 11px;
  line-height: 16px;
  cursor: pointer;
}

.flag-modal {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.4);
}

.flag-modal__box {
  width: 340px;
  padding: 16px;
  border-radius: 8px;
  background: #fff;
}

.flag-modal__title {
  margin: 0 0 10px;
  font-size: 16px;
}

.flag-modal__reasons {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 0 0 12px;
  padding: 8px 10px;
  border: 1px solid #e4e6eb;
  border-radius: 6px;
}

.flag-modal__legend {
  padding: 0 4px;
  color: #444;
  font-size: 12px;
}

.flag-modal__option {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
}

.flag-modal__detail {
  width: 100%;
  box-sizing: border-box;
  padding: 6px 10px;
  border: 1px solid #d9dbe0;
  border-radius: 6px;
  font-size: 13px;
  font-family: inherit;
}

.flag-modal__error {
  margin: 8px 0 0;
  color: #c0392b;
  font-size: 13px;
}

.flag-modal__actions {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}

.flag-modal__submit {
  padding: 6px 14px;
  border: none;
  border-radius: 6px;
  background: #c0392b;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}

.flag-modal__cancel {
  padding: 6px 14px;
  border: 1px solid #d9dbe0;
  border-radius: 6px;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
}
</style>
