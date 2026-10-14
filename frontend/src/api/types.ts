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

/** 招领启事的保管方式。 */
export type Custody = 'SELF' | 'AT_SECURITY'

export interface ReportImage {
  id: number
  path: string
  originalName: string
  mime: string
  sizeBytes: number
  sortOrder: number
}

export interface PublisherSummary {
  id: number
  nickname: string
  /** 仅当 contactPublic 为 true 时才有值，否则为 null。 */
  contactType: string | null
  contactValue: string | null
  contactPublic: boolean
}

/** 启事详情：含图片列表与发布者摘要。 */
export interface ReportDetail {
  id: number
  type: ReportType
  title: string
  category: string
  location: string
  locationDetail: string | null
  eventTime: string | null
  description: string | null
  status: ReportStatus
  pinned: boolean
  custody: Custody | null
  handoverNo: string | null
  viewCount: number
  publisher: PublisherSummary | null
  images: ReportImage[]
  createdAt: string
}

/** 发布启事请求体。 */
export interface CreateReportPayload {
  type: ReportType
  title: string
  category: string
  location: string
  locationDetail?: string
  eventTime: string
  description?: string
  custody?: Custody
  handoverNo?: string
}

export type UserRole = 'USER' | 'ADMIN'

export type UserStatus = 'ACTIVE' | 'DISABLED'

export interface User {
  id: number
  studentNo: string
  nickname: string
  department: string | null
  email: string | null
  role: UserRole
  status: UserStatus
  contactType: string | null
  contactValue: string | null
  contactPublic: boolean
  createdAt: string
}

/** 登录成功返回：access token 与当前用户。 */
export interface AuthResponse {
  token: string
  user: User
}
