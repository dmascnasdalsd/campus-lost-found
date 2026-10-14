import { getStoredToken, request } from './client'
import type {
  AdminStatsView,
  AuditLogView,
  FlagHandleResult,
  FlagView,
  PageResult,
  ReportDetail,
  ReportStatus
} from './types'

/** 复核队列筛选：留空表示查看全部启事，PENDING_REVIEW 表示只看待复核。 */
export type AdminReportFilter = '' | ReportStatus

/** 复核队列：status 缺省时返回全部启事。 */
export function listAdminReports(status?: string): Promise<PageResult<ReportDetail>> {
  const suffix = status ? `?status=${encodeURIComponent(status)}` : ''
  return request<PageResult<ReportDetail>>(`/admin/reports${suffix}`)
}

/** 复核通过：恢复为 OPEN。 */
export function approveReport(id: number | string): Promise<ReportDetail> {
  return request<ReportDetail>(`/admin/reports/${id}/approve`, { method: 'POST' })
}

/** 驳回：需填写原因。 */
export function rejectReport(id: number | string, reason: string): Promise<ReportDetail> {
  return request<ReportDetail>(`/admin/reports/${id}/reject`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  })
}

/** 下架：需填写原因。 */
export function takeDownReport(id: number | string, reason: string): Promise<ReportDetail> {
  return request<ReportDetail>(`/admin/reports/${id}/take-down`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  })
}

/** 置顶。 */
export function pinReport(id: number | string): Promise<ReportDetail> {
  return request<ReportDetail>(`/admin/reports/${id}/pin`, { method: 'POST' })
}

/** 取消置顶。 */
export function unpinReport(id: number | string): Promise<ReportDetail> {
  return request<ReportDetail>(`/admin/reports/${id}/unpin`, { method: 'POST' })
}

/** 审计日志：按时间倒序分页。 */
export function listAuditLogs(page = 0, size = 20): Promise<PageResult<AuditLogView>> {
  return request<PageResult<AuditLogView>>(`/admin/audit-logs?page=${page}&size=${size}`)
}

/** 举报列表：status 缺省时返回全部举报。 */
export function listAdminFlags(status?: string): Promise<PageResult<FlagView>> {
  const suffix = status ? `?status=${encodeURIComponent(status)}` : ''
  return request<PageResult<FlagView>>(`/admin/flags${suffix}`)
}

/** 处置举报：驳回举报 / 下架内容 / 禁用用户。 */
export function resolveFlag(id: number | string, action: FlagHandleResult): Promise<FlagView> {
  return request<FlagView>(`/admin/flags/${id}/resolve`, {
    method: 'POST',
    body: JSON.stringify({ action })
  })
}

/** 统计看板：range=week（最近 7 天）或 month（最近 30 天）。 */
export function getAdminStats(range: 'week' | 'month'): Promise<AdminStatsView> {
  return request<AdminStatsView>(`/admin/stats?range=${range}`)
}

/**
 * 导出 CSV：某时间段内的启事记录，缺省最近 30 天。
 * CSV 是文件流而非统一 JSON 响应，直接 fetch 并返回 Blob。
 */
export async function exportReportsCsv(from?: string, to?: string): Promise<Blob> {
  const params = new URLSearchParams()
  if (from) params.set('from', from)
  if (to) params.set('to', to)
  const query = params.toString()

  const headers: Record<string, string> = {}
  const token = getStoredToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const response = await fetch(`/api/admin/export.csv${query ? `?${query}` : ''}`, { headers })
  return response.blob()
}
