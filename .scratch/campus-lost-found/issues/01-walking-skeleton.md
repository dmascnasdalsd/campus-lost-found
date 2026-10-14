# 01: 走通骨架——一条启事从数据库到首页

**What to build:** 仓库一条命令能起后端与前端，数据库迁移建出基线表，任何人都能在首页看到启事列表（只读）。这一条同时把两侧的测试接缝立起来，后续所有 ticket 复用。

**Blocked by:** None (can start immediately)

**Status:** done

- [x] `backend/` 起一个 Spring Boot 应用，连上本机 MySQL 8 的 `lostfound` 库，Flyway 迁移建出 v1 基线表（app_user、report、report_image、claim、chat_session、message、notification、report_action、report_flag）
- [x] `GET /api/reports` 返回统一分页结构 `{ items, page, size, total }`，响应体走统一 `{ code, message, data }` 包装
- [x] `frontend/` 起一个 Vue 3 + Vite + TS 应用，首页请求 `/api/reports` 并渲染启事卡片；无数据时渲染空状态
- [x] 后端 REST API 集成测试 1 条（`@SpringBootTest` + 本机 MySQL 8 的 `lostfound_test` 库，断言 HTTP 响应与库中数据）
- [x] 前端组件测试 1 条（API client 被内存 fake 替换，断言首页空状态渲染）
- [x] 仓库根 `docker-compose.yml` 提供 MySQL；`README.md` 写清启动方式
- [x] `.data/uploads` 与构建产物进 `.gitignore`

## Comments

2026-10-14 实现完成并验证。

- 后端：`mvn -s .mvn/settings-public.xml -f backend/pom.xml test` → `Tests run: 2, Failures: 0, Errors: 0`
- 前端：`npm --prefix frontend test` → 3 passed；`npm --prefix frontend run typecheck` 通过
- 本机环境差异（已写入 README）：默认 `JAVA_HOME` 是 JDK 8，需显式切到 `C:\Users\86139\.jdks\jdk21`；全局 `~/.m2/settings.xml` 的镜像指向不可达的内网私服，改用 `.mvn/settings-public.xml`；Docker 守护进程不可用，测试库用本机 MySQL 8 的 `lostfound_test`。
- 实现中发现的问题：`open-in-view: false` 下，列表映射读取懒加载的 `Report.images` 会抛 `LazyInitializationException`（接口返回 500），已在只读查询方法上加 `@Transactional(readOnly = true)` 让 session 覆盖到映射结束。
