# 07: 认领处理与站内会话

**What to build:** 启事发布者能接受或拒绝认领申请；接受后启事进入认领中、其他待处理申请自动关闭并创建一个只对双方可见的会话，两人在会话里约定线下交还。

**Blocked by:** 06

**Status:** ready-for-agent

- [ ] `POST /api/claims/{id}/accept`：仅启事发布者或管理员可操作；启事状态变为 `CLAIMING`，该申请变为 `ACCEPTED`，同一启事下其他 `PENDING` 申请变为 `CLOSED`，并创建 `ChatSession`
- [ ] `POST /api/claims/{id}/reject`：仅启事发布者或管理员可操作，必须填写拒绝原因；申请变为 `REJECTED`
- [ ] 对非 `PENDING` 状态的申请重复 accept/reject 返回可辨识错误码
- [ ] `GET /api/chats` 返回我参与的会话；`GET /api/chats/{id}/messages` 与 `POST /api/chats/{id}/messages` 收发消息；非参与方访问一律返回越权错误码
- [ ] 交还完成后发布者把启事标记为已归还，同时会话关闭、认领申请变为 `CLOSED`
- [ ] 前端「我的认领」处理入口（接受 / 拒绝弹窗）、会话列表与会话页（消息流与输入框）
- [ ] 后端集成测试：非发布者 accept 被拒、accept 后其他申请被关闭且会话被创建、第三方读写会话被拒、reject 后申请人可重新申请
- [ ] 前端测试：处理弹窗交互、会话页渲染消息流
