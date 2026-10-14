# 校园失物招领系统

把「失物启事」与「招领启事」放到同一个可检索的地方，并把认领撮合流程固化下来的校内 Web 系统。

- 后端：Spring Boot 3.3 + Java 21 + Spring Data JPA + Flyway + MySQL 8
- 前端：Vue 3 + Vite + TypeScript

## 目录

```
backend/     Spring Boot 后端（REST API）
frontend/    Vue 3 前端（SPA）
docs/agents/ 工程技能读取的配置（issue tracker / 领域文档）
.scratch/    本地 issue tracker：spec 与工单
```

需求与工单见 [`.scratch/campus-lost-found/spec.md`](.scratch/campus-lost-found/spec.md) 与 `.scratch/campus-lost-found/issues/`。

## 环境要求

- JDK 21（本机在 `C:\Users\86139\.jdks\jdk21`，默认的 `JAVA_HOME` 指向 JDK 8，需要显式切到 21）
- Maven 3.9+
- Node.js 20+
- MySQL 8（本机 3306 已在运行）

## 准备数据库

开发库 `lostfound` 与测试库 `lostfound_test`，以及专用账号：

```sql
CREATE DATABASE IF NOT EXISTS lostfound DEFAULT CHARSET utf8mb4;
CREATE DATABASE IF NOT EXISTS lostfound_test DEFAULT CHARSET utf8mb4;
CREATE USER IF NOT EXISTS 'lostfound'@'%' IDENTIFIED BY 'lostfound';
GRANT ALL PRIVILEGES ON lostfound.* TO 'lostfound'@'%';
GRANT ALL PRIVILEGES ON lostfound_test.* TO 'lostfound'@'%';
FLUSH PRIVILEGES;
```

表结构由 Flyway 迁移管理，应用启动时自动建表（`backend/src/main/resources/db/migration/`）。

也可以改用容器里的 MySQL：`docker compose up -d`，然后连接 3307 端口。

连接信息可通过环境变量覆盖：`DB_URL`、`DB_USER`、`DB_PASSWORD`、`UPLOAD_DIR`。

## 启动后端

```powershell
$env:JAVA_HOME='C:\Users\86139\.jdks\jdk21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn -s .mvn/settings-public.xml -f backend/pom.xml spring-boot:run
```

> 本机全局 `~/.m2/settings.xml` 把镜像指向内网私服 `http://10.65.7.148:8081`，该地址在当前网络下不可达。
> `.mvn/settings-public.xml` 只把镜像指向公共仓库，用 `-s` 覆盖，不改动全局配置。

后端监听 `http://localhost:8080`。

## 启动前端

```powershell
npm --prefix frontend install
npm --prefix frontend run dev
```

前端监听 `http://localhost:5173`，`/api` 请求由 Vite 代理到 8080。

## 测试

```powershell
# 后端：走真实 HTTP 的接口集成测试，连接 lostfound_test 库
mvn -s .mvn/settings-public.xml -f backend/pom.xml test

# 前端：组件测试，API client 被内存 fake 替换
npm --prefix frontend test
```
