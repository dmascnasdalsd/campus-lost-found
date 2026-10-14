# 06: 认领申请

**What to build:** 用户能对一条启事发起认领申请（说明独有特征与期望交还时间地点），能在「我的认领」里看到自己发起与收到的申请，能在被处理前撤回。

**Blocked by:** 05

**Status:** ready-for-agent

- [ ] `POST /api/reports/{id}/claims`：提交独有特征说明与期望交还时间地点；不能对自己发布的启事发起申请
- [ ] 同一用户对同一启事最多只能有一条非终态（`PENDING` / `ACCEPTED`）申请；重复申请返回可辨识错误码
- [ ] 对状态为 `RETURNED` / `HANDED_OVER` / `OFFLINE` 的启事发起申请被拒
- [ ] `GET /api/claims?role=applied|received` 分别返回我发起 / 我收到的申请，含关联启事摘要与状态
- [ ] `POST /api/claims/{id}/withdraw` 撤回自己 `PENDING` 的申请；撤回后状态为 `WITHDRAWN`，用户可重新申请
- [ ] 前端详情页「申请认领」表单（独有特征、期望时间地点、字段级校验）与「我的认领」页面
- [ ] 后端集成测试：重复申请被拒、对自己启事申请被拒、对已归还启事申请被拒、撤回后可重新申请
- [ ] 前端测试：申请表单校验与提交成功后状态提示
