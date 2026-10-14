import { request } from './client'
import type { CreateFlagPayload, FlagView } from './types'

/** 举报启事。 */
export function createReportFlag(
  reportId: number | string,
  payload: CreateFlagPayload
): Promise<FlagView> {
  return request<FlagView>(`/reports/${reportId}/flags`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

/** 举报会话消息。 */
export function createMessageFlag(
  messageId: number | string,
  payload: CreateFlagPayload
): Promise<FlagView> {
  return request<FlagView>(`/messages/${messageId}/flags`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}
