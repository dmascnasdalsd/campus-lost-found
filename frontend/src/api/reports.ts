import { request } from './client'
import type { PageResult, ReportSummary } from './types'

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
