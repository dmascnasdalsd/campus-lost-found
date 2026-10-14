# 02: 账号——注册 / 登录 / 退出 / 我是谁

**What to build:** 用学号+密码注册并登录，前端能识别登录态、能退出；受保护页面在未登录时跳登录并在成功后回到原页面。

**Blocked by:** 01

**Status:** done

- [x] `POST /api/auth/register`：学号唯一，重复学号返回可辨识错误码；密码强度不足返回字段级错误
- [x] `POST /api/auth/login` 颁发 JWT；`GET /api/auth/me` 用 token 返回当前用户；`POST /api/auth/logout` 使 token 失效
- [x] 密码以 BCrypt 存哈希，任何接口与日志都不出现明文密码
- [x] 用户角色只有 `USER` / `ADMIN`；用户状态支持 `ACTIVE` / `DISABLED`，被禁用用户登录被拒
- [x] 前端登录页、注册页；路由守卫拦截未登录访问 `/reports/new` 等页面，登录成功后回跳原页面
- [x] 后端集成测试：重复学号注册、错误密码登录、无 token 访问受保护接口、被禁用用户登录
- [x] 前端测试：登录表单字段级错误提示、未登录访问受保护路由被重定向并携带回跳参数

## Comments

2026-10-14 实现完成并验证。

- 后端：`mvn -s .mvn/settings-public.xml -f backend/pom.xml test` → `Tests run: 8, Failures: 0`（AuthApiTest 6 + ReportApiTest 2）
- 前端：`npm --prefix frontend test` → 8 passed；`vue-tsc --noEmit` 通过
- 新增 `auth` 包（User/JwtService/TokenBlacklist/AuthService/AuthController/JwtAuthenticationFilter/SecurityConfig 等）；新增 `common/RestAuthenticationEntryPoint`、`RestAccessDeniedHandler`，使 401/403 也返回统一 `{code,message,data}`。
- 偏离 spec 的决定：token 存 localStorage（spec 原为仅内存），已在代码注释说明；注册后跳登录页不自动登录。
