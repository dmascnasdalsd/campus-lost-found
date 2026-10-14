import { request } from './client'
import type { ClaimView, CreateClaimRequest } from './types'

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
