# 08: 通知中心

**What to build:** 认领申请被创建、被接受、被拒绝、会话来了新消息时，相关用户收到站内通知；导航栏有未读角标，能一键全部标记已读。

**Blocked by:** 07

**Status:** done

- [ ] 认领申请创建 → 通知启事发布者；认领被接受 / 被拒绝 → 通知申请人；会话新消息 → 通知会话另一方；启事被下架 / 被驳回 → 通知发布者
- [ ] `GET /api/notifications` 返回通知列表与未读数；`POST /api/notifications/{id}/read` 与 `POST /api/notifications/read-all` 标记已读
- [ ] 通知模型不随实现变化：`Notification` 记录接收人、类型、关联对象（启事 / 认领 / 会话）、已读标记、时间
- [ ] 前端导航栏未读角标（轮询刷新）、通知列表页、全部标记已读
- [ ] 后端集成测试：各业务事件产生正确通知、标记已读后未读数归零、通知只发给相关用户而非全体
- [ ] 前端测试：未读角标渲染与标记已读后归零

## Comments

- 新增实体 `Notification`/`NotificationType`/`NotificationTargetType`、`NotificationRepository`、`NotificationService`、`NotificationController`（`GET /api/notifications`、`POST /{id}/read`、`POST /read-all`）、`dto/NotificationView`+`NotificationListView`。
- 事件投递：认领创建→发布者 `CLAIM_CREATED`；接受→申请人 `CLAIM_ACCEPTED`；被自动关闭的其他申请→其申请人 `CLAIM_CLOSED`；驳回→申请人 `CLAIM_REJECTED`；会话新消息→另一方 `MESSAGE_RECEIVED`；管理员下架/驳回→发布者 `REPORT_TAKE_DOWN`/`REPORT_REJECTED`。仅追加投递调用，未改既有业务规则。
- 前端新增 `/notifications` 页与 Pinia `notificationStore`；`App.vue` 导航加未读角标 + 30s 轮询（卸载清定时器，未登录不轮询）。
- 测试：后端 `NotificationApiTest` 9（合计 74 全绿）；前端 54（12 文件）全绿 + typecheck 通过。
