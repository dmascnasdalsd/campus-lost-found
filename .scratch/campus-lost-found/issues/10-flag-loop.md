# 10: 举报闭环

**What to build:** 用户能举报一条启事或一条会话消息并选择理由；管理员能在举报列表里处理——驳回举报、下架内容或禁用用户。

**Blocked by:** 09, 07

**Status:** done

- [ ] `POST /api/reports/{id}/flags` 与 `POST /api/messages/{id}/flags`：理由只能取「虚假信息 / 广告 / 辱骂 / 其他」，可选补充说明
- [ ] 举报成功后，被举报启事进入待复核状态并出现在管理员队列中
- [ ] `GET /api/admin/flags` 返回举报列表（含被举报内容摘要、理由、举报人、时间）；`POST /api/admin/flags/{id}/resolve` 支持「驳回举报 / 下架内容 / 禁用用户」三种处置并记录结果
- [ ] 处置后 `ReportFlag` 状态为 `RESOLVED` / `DISMISSED`，重复处置返回可辨识错误码
- [ ] 前端：详情页与会话页的举报入口（理由选择弹窗）、管理端举报列表与处置操作
- [ ] 后端集成测试：举报后启事进入待复核、处置下架后启事不可见、禁用用户后该用户无法登录、非管理员访问举报接口被拒
- [ ] 前端测试：举报弹窗提交后提示、管理端举报列表按状态渲染

## Comments

- 新增实体 `ReportFlag`+`FlagTargetType`/`FlagReason`/`FlagStatus`/`FlagHandleResult`、`ReportFlagRepository`、`dto/CreateFlagRequest`+`FlagView`+`ResolveFlagRequest`、`service/FlagService`、`controller/FlagController`（`POST /api/reports/{id}/flags`、`POST /api/messages/{id}/flags`）、`controller/AdminFlagController`（`GET /api/admin/flags`、`POST /api/admin/flags/{id}/resolve`）。
- 举报理由限定为 `FAKE`/`AD`/`ABUSE`/`OTHER`（枚举绑定，非法值 1001）；补充说明可选。
- 举报启事后，若启事为 `OPEN`/`CLAIMING` 则置 `PENDING_REVIEW` 进入复核队列（终态不复活）。
- `AdminService` 新增 `listFlags`/`resolveFlag`：驳回`DISMISS`、下架`TAKE_DOWN`（启事复用既有 `takeDown` 并写审计；消息直接删除）、禁用用户`DISABLE_USER`（按目标类型定位发布者/发送者置 `DISABLED`）；重复处置返回 1005。
- 前端：详情页与会话页举报入口（理由弹窗）、`/admin/flags` 管理端处置页与导航入口。
- 测试：后端 `FlagApiTest` 8（合计 82 全绿）；前端 60（13 文件）全绿 + typecheck 通过。
