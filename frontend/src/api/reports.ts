import { request } from './client'
import type { CreateReportPayload, PageResult, ReportDetail, ReportSummary } from './types'

export interface ListReportsParams {
  page?: number
  size?: number
}

export function listReports(params: ListReportsParams = {}): Promise<PageResult<ReportSummary>> {
  const query = new URLSearchParams()
  if (params.page !== undefined) {
    query.set('page', String(params.page))
  }
  if (params.size !== undefined) {
    query.set('size', String(params.size))
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
