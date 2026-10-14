import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import ChatPage from './ChatPage.vue'
import type { MessageView, User } from '@/api/types'
import { useAuthStore } from '@/stores/auth'

// API 模块被内存 fake 替换：测试只断言「给定 API 返回，用户看到什么、点击后发出什么请求」。
const { listMessages, sendMessage } = vi.hoisted(() => ({
  listMessages: vi.fn(),
  sendMessage: vi.fn()
}))

vi.mock('@/api/chats', () => ({ listMessages, sendMessage }))

const routes: RouteRecordRaw[] = [{ path: '/me/chats/:id', component: { template: '<div />' } }]

function messageView(overrides: Partial<MessageView> = {}): MessageView {
  return {
    id: 1,
    sessionId: 1,
    senderId: 1,
    senderNickname: '发布者',
    body: '你好，我捡到了你的校园卡',
    createdAt: '2026-10-14T09:00:00',
    ...overrides
  }
}

function currentUser(id: number): User {
  return {
    id,
    studentNo: '2021001',
    nickname: '发布者',
    department: null,
    email: null,
    role: 'USER',
    status: 'ACTIVE',
    contactType: null,
    contactValue: null,
    contactPublic: false,
    createdAt: '2026-10-14T09:00:00'
  }
}

async function mountPage() {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/me/chats/1')
  await router.isReady()
  const wrapper = mount(ChatPage, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

describe('ChatPage', () => {
  beforeEach(() => {
    listMessages.mockReset()
    sendMessage.mockReset()
    const pinia = createPinia()
    setActivePinia(pinia)
    useAuthStore().user = currentUser(1)
  })

  it('渲染消息流并区分自己与对方', async () => {
    listMessages.mockResolvedValue([
      messageView({ id: 1, senderId: 1, body: '你好，我捡到了你的校园卡' }),
      messageView({ id: 2, senderId: 2, senderNickname: '申请人', body: '太好了，我这就来取' })
    ])

    const wrapper = await mountPage()

    expect(listMessages).toHaveBeenCalledWith('1')
    expect(wrapper.text()).toContain('你好，我捡到了你的校园卡')
    expect(wrapper.text()).toContain('太好了，我这就来取')
    expect(wrapper.find('.message--self').text()).toContain('你好，我捡到了你的校园卡')
    expect(wrapper.find('.message--peer').text()).toContain('太好了，我这就来取')
  })

  it('输入并点击发送会调用 sendMessage 并追加消息', async () => {
    listMessages.mockResolvedValue([messageView({ id: 1, senderId: 1 })])
    sendMessage.mockResolvedValue(
      messageView({ id: 2, senderId: 1, body: '在的，随时可以' })
    )

    const wrapper = await mountPage()
    await wrapper.find('.chat__input').setValue('在的，随时可以')
    await wrapper.find('.chat__send').trigger('click')
    await flushPromises()

    expect(sendMessage).toHaveBeenCalledWith('1', { body: '在的，随时可以' })
    expect(wrapper.text()).toContain('在的，随时可以')
  })

  it('内容为空时不发出请求', async () => {
    listMessages.mockResolvedValue([])

    const wrapper = await mountPage()
    await wrapper.find('.chat__send').trigger('click')
    await flushPromises()

    expect(sendMessage).not.toHaveBeenCalled()
  })
})
