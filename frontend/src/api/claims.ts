import { request } from './client'
import type { ClaimView, CreateClaimRequest, RejectClaimPayload } from './types'

/** 「我的认领」筛选：applied=我发起的，received=我收到的。 */
export type ClaimRole = 'applied' | 'received'

export function createClaim(
  reportId: number | string,
  payload: CreateClaimRequest
): Promise<ClaimView> {
  return request<ClaimView>(`/reports/${reportId}/claims`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function listClaims(role: ClaimRole): Promise<ClaimView[]> {
  return request<ClaimView[]>(`/claims?role=${role}`)
}

export function withdrawClaim(id: number | string): Promise<ClaimView> {
  return request<ClaimView>(`/claims/${id}/withdraw`, { method: 'POST' })
}

/** 接受认领申请：仅启事发布者或管理员。 */
export function acceptClaim(id: number | string): Promise<ClaimView> {
  return request<ClaimView>(`/claims/${id}/accept`, { method: 'POST' })
}

/** 驳回认领申请：仅启事发布者或管理员，必须填写驳回原因。 */
export function rejectClaim(
  id: number | string,
  payload: RejectClaimPayload
): Promise<ClaimView> {
  return request<ClaimView>(`/claims/${id}/reject`, {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}
