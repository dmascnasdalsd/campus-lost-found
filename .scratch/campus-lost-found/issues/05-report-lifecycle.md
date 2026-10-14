# 05: 启事生命周期——编辑、删除、标记归还/移交

**What to build:** 发布者能在发布后 30 分钟内修改或删除自己的启事；能把自己的启事标记为已归还或已移交（交保卫处）；越权与超时都被明确拒绝。

**Blocked by:** 04

**Status:** done

- [x] `PATCH /api/reports/{id}` 修改启事，仅发布者或管理员可操作，仅允许在发布后 30 分钟内（超时返回可辨识错误码）
- [x] `DELETE /api/reports/{id}` 删除启事，同上权限与时间窗
- [x] `PUT /api/reports/{id}/status` 支持 `RETURNED`（已归还）与 `HANDED_OVER`（已移交，招领启事专用，必须带移交单号）
- [x] 非发布者、非管理员调用以上接口一律返回越权错误码
- [x] 前端「我的发布」页面：列出自己的全部启事与实时状态徽标，提供编辑、删除、标记归还 / 移交操作，并显示 30 分钟编辑窗口剩余情况
- [x] 后端集成测试：越权编辑被拒、超时编辑被拒、标记归还需要确认、标记已归还在默认列表中消失
- [x] 前端测试：「我的发布」渲染不同状态徽标与对应可操作按钮

## Comments

2026-10-14 实现完成并验证。

- 后端 `Tests run: 33, Failures: 0`（新增 ReportLifecycleApiTest 16 条）；前端 `Test Files 5 passed / Tests 21 passed`；`vue-tsc --noEmit` 通过。
- 接口：`PATCH /api/reports/{id}`、`DELETE /api/reports/{id}`（均限发布后 30 分钟，越权 1004 / 超时 1005）、`PUT /api/reports/{id}/status`（RETURNED / HANDED_OVER，HANDED_OVER 仅 FOUND 且必带 handoverNo，不受时间窗限制）、`GET /api/me/reports`（本人全部启事，避开 `/api/reports/**` 的 permitAll）。
- 新增 `UpdateReportRequest`、`UpdateStatusRequest`、`MeReportController`；`ReportDetail` 补 `rejectReason/reviewedAt`，`ReportSummary` 补 `custody`。
- 前端新增 `MyReportsPage.vue`（/me/reports）+ 导航入口；`api/reports.ts` 加 updateReport/deleteReport/updateReportStatus/listMyReports。
- 偏离：TestRestTemplate 底层 HttpURLConnection 不支持 PATCH，故 PATCH 用例单独用 JDK HttpClient 构造请求（仅测试内，不改外部行为）；30 分钟窗口对 ADMIN 同样生效。
