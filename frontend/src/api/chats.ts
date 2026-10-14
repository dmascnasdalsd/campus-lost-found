import { request } from './client'
import type { ChatSessionView, MessageView, SendMessageRequest } from './types'

/** 我参与的会话列表。 */
export function listChats(): Promise<ChatSessionView[]> {
  return request<ChatSessionView[]>('/chats')
}

/** 某会话的消息流。 */
export function listMessages(sessionId: number | string): Promise<MessageView[]> {
  return request<MessageView[]>(`/chats/${sessionId}/messages`)
}

/** 发送消息。 */
export function sendMessage(
  sessionId: number | string,
  payload: SendMessageRequest
): Promise<MessageView> {
  return request<MessageView>(`/chats/${sessionId}/messages`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}
