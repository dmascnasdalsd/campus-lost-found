# 09: 管理员内容治理——复核队列、下架、置顶、审计

**What to build:** 管理员能看到待复核的启事队列与举报内容，能通过、驳回、下架或置顶任意启事；所有治理动作留下审计记录，普通用户访问管理接口一律被拒。

**Blocked by:** 05

**Status:** done

- [ ] 待复核队列 `GET /api/admin/reports`：按状态筛选，v1 至少支持 `PENDING_REVIEW`（被举报或管理员下架待复核的启事）与全部启事查看
- [ ] `POST /api/admin/reports/{id}/approve` 恢复为 `OPEN`、`POST /api/admin/reports/{id}/reject` 置为 `REJECTED` 并记录驳回原因、`POST /api/admin/reports/{id}/take-down` 置为 `OFFLINE`、`POST /api/admin/reports/{id}/pin` 与 `unpin` 控制置顶
- [ ] 置顶启事在列表排序中优先于普通启事
- [ ] 每次治理动作写入 `ReportAction` 审计记录（操作人、动作、目标启事、原因、时间），`GET /api/admin/audit-logs` 可查
- [ ] `/api/admin/**` 仅 `ADMIN` 角色可访问，`USER` 或访客访问返回越权错误码
- [ ] 前端管理端：复核队列页、启事操作按钮、审计日志页
- [ ] 后端集成测试：越权访问被拒、下架后启事在默认列表不可见、approve 后恢复可见、审计记录落库且内容正确
- [ ] 前端测试：管理端队列渲染与操作按钮按状态启用/禁用

## Comments

- 新增 `entity/ReportAction`+`ReportActionType`、`repository/ReportActionRepository`、`dto/AdminActionRequest`+`AuditLogView`、`service/AdminService`、`controller/AdminController`（`/api/admin/reports`、各治理动作、`/api/admin/audit-logs`）。
- 复核队列复用 `ReportDetail`（含图片与描述）；approve/pin 无请求体，reject/take-down 用 `@Valid AdminActionRequest`（reason 必填）。
- 每次治理动作写 `report_action` 审计；置顶排序沿用既有 `resolveSort`（pinned 优先），未改动。
- `/api/admin/**` 角色限制由既有 SecurityConfig 承担；前端新增 `/admin/reports`、`/admin/audit-logs` 路由与 `requiresAdmin` 守卫，ADMIN 才显示「管理后台」入口。
- 测试：后端 `AdminApiTest` 10（合计 65 全绿）；前端 46 全绿 + typecheck 通过。
