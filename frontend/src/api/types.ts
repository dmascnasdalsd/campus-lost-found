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

/** 驳回认领申请请求体：驳回原因必填。 */
export interface RejectClaimPayload {
  rejectReason: string
}

/** 站内会话状态：OPEN 进行中，CLOSED 已关闭。 */
export type ChatSessionStatus = 'OPEN' | 'CLOSED'

/** 会话视图：含关联启事摘要与对方昵称。 */
export interface ChatSessionView {
  id: number
  reportId: number
  report: ReportSummary | null
  peerNickname: string | null
  status: ChatSessionStatus
  createdAt: string
  closedAt: string | null
}

/** 会话消息视图：含发送者昵称。 */
export interface MessageView {
  id: number
  sessionId: number
  senderId: number
  senderNickname: string | null
  body: string
  createdAt: string
}

/** 发送消息请求体。 */
export interface SendMessageRequest {
  body: string
}

/** 站内通知的业务类型。 */
export type NotificationType =
  | 'CLAIM_CREATED'
  | 'CLAIM_ACCEPTED'
  | 'CLAIM_REJECTED'
  | 'CLAIM_CLOSED'
  | 'MESSAGE_RECEIVED'
  | 'REPORT_TAKE_DOWN'
  | 'REPORT_REJECTED'

/** 通知关联对象的类型。 */
export type NotificationTargetType = 'REPORT' | 'CLAIM' | 'SESSION'

/** 单条通知视图：含类型、关联对象、已读标记与时间。 */
export interface NotificationView {
  id: number
  type: NotificationType
  targetType: NotificationTargetType | null
  targetId: number | null
  read: boolean
  createdAt: string
}

/** 通知列表视图：通知条目与当前未读数。 */
export interface NotificationListView {
  items: NotificationView[]
  unreadCount: number
}

/** 管理员内容治理动作类型。 */
export type ReportActionType = 'APPROVE' | 'REJECT' | 'TAKE_DOWN' | 'PIN' | 'UNPIN'

/** 举报目标类型：启事 / 会话消息。 */
export type FlagTargetType = 'REPORT' | 'MESSAGE'

/** 举报理由。 */
export type FlagReason = 'FAKE' | 'AD' | 'ABUSE' | 'OTHER'

/** 举报处理状态。 */
export type FlagStatus = 'PENDING' | 'RESOLVED' | 'DISMISSED'

/** 举报处置结果：驳回举报 / 下架内容 / 禁用用户。 */
export type FlagHandleResult = 'DISMISS' | 'TAKE_DOWN' | 'DISABLE_USER'

/** 发起举报请求体：理由必填，补充说明可选。 */
export interface CreateFlagPayload {
  reason: FlagReason
  detail?: string
}

/** 举报视图：含被举报内容摘要、举报人与处置信息。 */
export interface FlagView {
  id: number
  targetType: FlagTargetType
  targetId: number
  targetSummary: string | null
  reason: FlagReason
  detail: string | null
  status: FlagStatus
  handleResult: FlagHandleResult | null
  reporterId: number
  reporterNickname: string | null
  handledBy: number | null
  handledAt: string | null
  createdAt: string
}

/** 审计日志视图：含操作人昵称与目标启事标题。 */
export interface AuditLogView {
  id: number
  operatorId: number
  operatorNickname: string | null
  action: ReportActionType
  reportId: number
  reportTitle: string | null
  reason: string | null
  createdAt: string
}

/** 每日统计：某一天的新增启事数与归还成功数。 */
export interface DailyStatView {
  date: string
  created: number
  returned: number
}

/** 名称计数：用于地点 / 分类 / 高发时段的分布统计。 */
export interface NameCountView {
  name: string
  count: number
}

/** 统计看板视图：某统计窗口内的启事聚合结果。 */
export interface AdminStatsView {
  range: string
  from: string
  to: string
  totalReports: number
  returnedReports: number
  /** 归还率，百分比数值（0~100，保留 1 位小数）。 */
  returnRate: number
  daily: DailyStatView[]
  byLocation: NameCountView[]
  byCategory: NameCountView[]
  byHour: NameCountView[]
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
