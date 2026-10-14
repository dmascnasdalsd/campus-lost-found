import { request } from './client'
import type {
  CreateReportPayload,
  PageResult,
  ReportDetail,
  ReportStatusUpdatePayload,
  ReportSummary,
  ReportType,
  UpdateReportPayload
} from './types'

export type ReportTimeRange = 'today' | '3d' | '7d' | '30d' | 'custom'

export type ReportSort = 'latest' | 'eventTime'

export interface ListReportsParams {
  type?: ReportType
  category?: string
  location?: string
  timeRange?: ReportTimeRange
  /** 仅 timeRange=custom 时有效，ISO 字符串（yyyy-MM-ddTHH:mm:ss）。 */
  startTime?: string
  endTime?: string
  keyword?: string
  sort?: ReportSort
  page?: number
  size?: number
  includeClosed?: boolean
}

export function listReports(params: ListReportsParams = {}): Promise<PageResult<ReportSummary>> {
  const query = new URLSearchParams()
  if (params.type) {
    query.set('type', params.type)
  }
  if (params.category) {
    query.set('category', params.category)
  }
  if (params.location) {
    query.set('location', params.location)
  }
  if (params.timeRange) {
    query.set('timeRange', params.timeRange)
  }
  if (params.startTime) {
    query.set('startTime', params.startTime)
  }
  if (params.endTime) {
    query.set('endTime', params.endTime)
  }
  if (params.keyword) {
    query.set('keyword', params.keyword)
  }
  if (params.sort) {
    query.set('sort', params.sort)
  }
  if (params.page !== undefined) {
    query.set('page', String(params.page))
  }
  if (params.size !== undefined) {
    query.set('size', String(params.size))
  }
  if (params.includeClosed) {
    query.set('includeClosed', 'true')
  }
  const suffix = query.toString() === '' ? '' : `?${query.toString()}`
  return request<PageResult<ReportSummary>>(`/reports${suffix}`)
}

export function createReport(payload: CreateReportPayload): Promise<ReportDetail> {
  return request<ReportDetail>('/reports', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function getReport(id: number | string): Promise<ReportDetail> {
  return request<ReportDetail>(`/reports/${id}`)
}

/** 上传图片：multipart，不手动设 Content-Type，交给浏览器带 boundary。 */
export function uploadImages(reportId: number | string, files: File[]): Promise<ReportDetail> {
  const form = new FormData()
  files.forEach((file) => form.append('files', file))
  return request<ReportDetail>(`/reports/${reportId}/images`, {
    method: 'POST',
    body: form
  })
}

/** 修改启事：仅发布者 / 管理员，且需在发布后 30 分钟内。 */
export function updateReport(id: number | string, payload: UpdateReportPayload): Promise<ReportDetail> {
  return request<ReportDetail>(`/reports/${id}`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  })
}

/** 删除启事：仅发布者 / 管理员，且需在发布后 30 分钟内。 */
export function deleteReport(id: number | string): Promise<void> {
  return request<void>(`/reports/${id}`, { method: 'DELETE' })
}

/** 标记已归还 / 已移交；已移交需带移交单号。 */
export function updateReportStatus(
  id: number | string,
  status: ReportStatusUpdatePayload['status'],
  handoverNo?: string
): Promise<ReportDetail> {
  const payload: ReportStatusUpdatePayload = handoverNo ? { status, handoverNo } : { status }
  return request<ReportDetail>(`/reports/${id}/status`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

/** 我的发布：当前用户的全部启事（含终态），按发布时间倒序。 */
export function listMyReports(): Promise<PageResult<ReportSummary>> {
  return request<PageResult<ReportSummary>>('/me/reports')
}
