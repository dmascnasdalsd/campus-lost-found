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
  custody: Custody | null
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
  rejectReason: string | null
  reviewedAt: string | null
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

/** 修改启事请求体（PATCH）：缺省字段表示不改动。 */
export interface UpdateReportPayload {
  title?: string
  category?: string
  location?: string
  locationDetail?: string
  eventTime?: string
  description?: string
  custody?: Custody
  handoverNo?: string
}

/** 变更启事状态请求体：仅支持已归还 / 已移交。 */
export interface ReportStatusUpdatePayload {
  status: 'RETURNED' | 'HANDED_OVER'
  handoverNo?: string
}

export type ClaimStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'WITHDRAWN' | 'CLOSED'

/** 认领方向：APPLY_CLAIM 针对招领启事，APPLY_MATCH 针对失物启事。 */
export type ClaimDirection = 'APPLY_CLAIM' | 'APPLY_MATCH'

/** 认领申请视图：含关联启事摘要与申请人昵称。 */
export interface ClaimView {
  id: number
  direction: ClaimDirection
  status: ClaimStatus
  featureDesc: string
  expectTime: string | null
  expectPlace: string | null
  rejectReason: string | null
  handledAt: string | null
  createdAt: string
  report: ReportSummary | null
  applicantId: number
  applicantNickname: string | null
}

/** 发起认领申请请求体：独有特征必填，期望时间地点可选。 */
export interface CreateClaimRequest {
  featureDesc: string
  expectTime?: string
  expectPlace?: string
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
