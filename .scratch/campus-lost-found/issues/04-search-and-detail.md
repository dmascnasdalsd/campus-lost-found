# 04: 检索与详情页

**What to build:** 访客能按类型、分类、地点、时间范围、关键词筛选并排序、翻页；点进详情页能看到完整描述、大图与发布者公开的联系方式。

**Blocked by:** 03

**Status:** ready-for-agent

- [ ] `GET /api/reports` 支持 `type`、`category`、`location`、`timeRange`（today/3d/7d/30d/自定义起止）、`keyword`（匹配物品名与描述）、`sort`（最新发布 / 最近事件时间）、`page`、`size`、`includeClosed`
- [ ] 已归还 / 已移交 / 已下架的启事默认不出现在列表中；带 `includeClosed` 时可见
- [ ] `GET /api/reports/{id}` 返回详情并使浏览量 +1；浏览量在详情响应中可见
- [ ] 详情页只在发布者允许公开联系方式时展示联系方式，否则展示「通过站内会话联系」
- [ ] 前端列表页：筛选控件、排序、分页器，筛选条件同步到 URL query，刷新与分享后筛选保持
- [ ] 前端详情页：大图、状态徽标、浏览量、发布者信息与公开联系方式
- [ ] 后端集成测试：关键词命中与未命中、多条件组合筛选、`includeClosed` 开关前后结果差异、浏览量自增
- [ ] 前端测试：改筛选后请求参数正确、URL query 同步
