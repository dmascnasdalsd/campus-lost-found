# 11: 管理员统计看板与 CSV 导出

**What to build:** 管理员能看到每日新增启事数、归还数、归还率、按地点与分类的分布、按小时的高发分布，并能按周/月切换；能把某时间段的启事与归还记录导出为 CSV。

**Blocked by:** 09

**Status:** done

- [ ] `GET /api/admin/stats?range=week|month`：返回新增启事数、归还成功数、归还率、按地点的计数分布、按分类的计数分布、按小时（0–23）的计数分布
- [ ] 统计数据以聚合查询直接计算，v1 不引入缓存或物化表
- [ ] `GET /api/admin/export.csv` 按时间范围导出启事与归还记录，返回 `text/csv` 且含正确表头
- [ ] 仅 `ADMIN` 可访问统计与导出接口
- [ ] 前端管理端统计页：数字卡片、按地点/分类/小时分布的图表、周/月切换、导出按钮
- [ ] 后端集成测试：给定夹具数据后各项统计数值正确、归还率计算正确、CSV 表头与行数正确、越权访问被拒
- [ ] 前端测试：统计页渲染数值与切换 range 后重新请求

## Comments

- 新增 dto `DailyStatView`/`NameCountView`/`AdminStatsView`；`ReportRepository` 增 `findByCreatedAtBetweenOrderByCreatedAtAsc`；`AdminService` 增 `stats(range)` 与 `exportCsv(from,to)`；`AdminController` 增 `GET /api/admin/stats`、`GET /api/admin/export.csv`。
- `range=week`（近 7 天）/`month`（近 30 天），缺省 week，非法值 1001；窗口内按 `created_at` 过滤，未硬编码、未建物化表。
- 每日序列按天补 0；`byLocation`/`byCategory` 计数倒序；`byHour` 按 `event_time` 小时聚合、0–23 全量补 0；`returnRate` 为百分比数值（0~100，保留 1 位小数）。
- CSV 直接返回 `text/csv;charset=UTF-8` + `attachment; filename="lostfound-export.csv"`，含 UTF-8 BOM 与中文表头，字段按 CSV 规则转义。
- 前端新增 `/admin/stats` 看板页（周/月切换、数字卡片、轻量分布展示、导出按钮），导航加「统计看板」；CSV 经 `fetch` 取 Blob 后前端触发下载。
- 测试：后端 `StatsApiTest` 7（合计 89 全绿）；前端 65（14 文件）全绿 + typecheck 通过。
