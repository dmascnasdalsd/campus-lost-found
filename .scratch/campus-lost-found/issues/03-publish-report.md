# 03: 发布启事（含分类/地点词表与图片上传）

**What to build:** 登录用户能发布一条失物启事或招领启事，填物品名、分类、地点、事件时间、描述，上传 1～3 张图片，发布后能立刻在列表与详情里看到。

**Blocked by:** 02

**Status:** done

- [x] `POST /api/reports` 写入启事并返回详情；`type` 只能是 `LOST` / `FOUND`
- [x] 分类与地点必须取自词表（分类：校园卡、身份证、钥匙、耳机、手机、笔记本电脑、充电宝、水杯、书籍、雨伞、衣物、运动器材、其他；地点：教学楼、图书馆、食堂、宿舍楼、体育馆/操场、校医院、校门、其他），越界值被拒并返回字段级错误
- [x] 招领启事的保管方式 `SELF` / `AT_SECURITY` 必填，`AT_SECURITY` 时移交单号必填
- [x] `POST /api/reports/{id}/images` 上传 1～3 张图片到本地磁盘目录（路径可配置），库中只存元数据；超过 3 张或单张 > 5MB 被拒
- [x] 前端发布页：分类/地点下拉、时间选择、描述、多图选择与本地预览与排序、提交前校验，提交成功后跳转到详情页
- [x] 后端集成测试：非法分类被拒、图片超数量被拒、合法发布后出现在 `GET /api/reports` 结果中
- [x] 前端测试：表单必填校验提示、选图后出现预览缩略图

## Comments

2026-10-14 实现完成并验证。

- 后端：`Tests run: 12, Failures: 0`（ReportApiTest 6 条）
- 前端：`Test Files 4 passed / Tests 10 passed`；`vue-tsc --noEmit` 通过
- 新增 `report/ReportVocabulary`、`CreateReportRequest`、`ReportDetail`/`ReportImageInfo`/`PublisherSummary`、`common/WebMvcConfig`（`/uploads/**` → `file:${app.upload.dir}/`）。
- 顺带提供最小 `GET /api/reports/{id}`（不含浏览量自增与联系方式展示，留给工单 04）；前端新增常量词表 `constants/vocabulary.ts`、最小详情页 `ReportDetailPage.vue`。
- 偏离说明：发布页把「1～3 张图片」做成前端必填（未选图提示至少 1 张）；分类/地点越界用 Service 层 `BusinessException.invalidParam`（消息含字段名）实现字段级错误。
