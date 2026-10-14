export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export type ReportType = 'LOST' | 'FOUND'

export type ReportStatus =
  | 'OPEN'
  | 'PENDING_REVIEW'
  | 'CLAIMING'
  | 'RETURNED'
  | 'HANDED_OVER'
  | 'OFFLINE'
  | 'REJECTED'

export interface ReportSummary {
  id: number
  type: ReportType
  title: string
  category: string
  location: string
  locationDetail: string | null
  eventTime: string | null
  status: ReportStatus
  pinned: boolean
  thumbnailPath: string | null
  viewCount: number
  createdAt: string
}
