# 06: 认领申请

**What to build:** 用户能对一条启事发起认领申请（说明独有特征与期望交还时间地点），能在「我的认领」里看到自己发起与收到的申请，能在被处理前撤回。

**Blocked by:** 05

**Status:** done

- [x] `POST /api/reports/{id}/claims`：提交独有特征说明与期望交还时间地点；不能对自己发布的启事发起申请
- [x] 同一用户对同一启事最多只能有一条非终态（`PENDING` / `ACCEPTED`）申请；重复申请返回可辨识错误码
- [x] 对状态为 `RETURNED` / `HANDED_OVER` / `OFFLINE` 的启事发起申请被拒
- [x] `GET /api/claims?role=applied|received` 分别返回我发起 / 我收到的申请，含关联启事摘要与状态
- [x] `POST /api/claims/{id}/withdraw` 撤回自己 `PENDING` 的申请；撤回后状态为 `WITHDRAWN`，用户可重新申请
- [x] 前端详情页「申请认领」表单（独有特征、期望时间地点、字段级校验）与「我的认领」页面
- [x] 后端集成测试：重复申请被拒、对自己启事申请被拒、对已归还启事申请被拒、撤回后可重新申请
- [x] 前端测试：申请表单校验与提交成功后状态提示

## Comments

- 实现：后端新增 `entity/Claim`、`ClaimStatus`、`ClaimDirection`，`repository/ClaimRepository`，`dto/CreateClaimRequest`、`ClaimView`，`service/ClaimService`，`controller/ClaimController`（`@RequestMapping("/api")`，路由 `POST /reports/{id}/claims`、`GET /claims`、`POST /claims/{id}/withdraw`）。未改 `SecurityConfig`、未新增 migration（`claim` 表已在 `V1__baseline.sql` 建好）。
- 规则落地：校验顺序＝不存在(1002)→本人发布(1004)→已结束 RETURNED/HANDED_OVER/OFFLINE(1001)→已有非终态申请(1005)。`direction` 由启事类型推导：`FOUND → APPLY_CLAIM`、`LOST → APPLY_MATCH`。撤回仅限申请人本人且仅 `PENDING`。
- 前端：新增 `api/claims.ts`、`pages/MyClaimsPage.vue`（`/me/claims`，双 tab，仅我发起的 `PENDING` 可撤回）、`App.vue` 导航入口；`ReportDetailPage.vue` 增加「申请认领」表单（独有特征必填 + 字段级中文校验 + 成功提示），未登录引导登录并带 `redirect` 回跳，发布者本人隐藏入口。
- 测试：后端 `ClaimApiTest` 9 条（重复申请 1005、对自己启事 1004、对已归还启事 4xx、撤回后可重新申请、未登录 401、applied/received 集合、direction 落库、非法 role、非申请人撤回）；前端新增 `MyClaimsPage.spec.ts`、`ReportDetailPage.spec.ts`。
- 验证结果：`mvn -s .mvn/settings-public.xml -f backend/pom.xml -B test` → Tests run: 42, Failures: 0, Errors: 0；`npm --prefix frontend test` → 7 文件 / 31 测试通过；`npm --prefix frontend run typecheck` → 通过。
- 偏离：无。
