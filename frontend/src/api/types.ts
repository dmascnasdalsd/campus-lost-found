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
