# 12: 移动端响应式与加载/空/错误态打磨

**What to build:** 在手机宽度下页面正常可用；列表与详情在慢网下有骨架屏，空结果与请求失败都有明确的中文提示与重试入口。

**Blocked by:** 11, 10

**Status:** done

- [ ] 首页、列表页、详情页、发布页在 ≤768px 宽度下布局可用（导航折叠、卡片单列、表单不溢出）
- [ ] 列表与详情加载中显示骨架屏，而不是空白闪烁
- [ ] 空结果展示引导（如「没有找到？发布一条失物启事」），请求失败展示错误提示与重试按钮
- [ ] 图片在列表卡片中按比例裁切，不因原图尺寸而撑破布局
- [ ] 前端测试：窄屏渲染快照或布局断言、加载态与错误态渲染、重试点击后重新请求

## Comments

- 新增 `components/LoadingSkeleton.vue`（列表/详情骨架屏，微光动画）与 `components/ErrorState.vue`（中文错误提示 + 重试按钮）；`EmptyState.vue` 扩展 `title/hint/actionText/actionTo`（向后兼容，HomePage 空态 CTA 指向 `/reports/new`）。
- 首页/详情页及各列表页（我的发布、我的认领、我的会话、通知、管理端各页）统一补齐「加载骨架 / 空态引导 / 错误重试」三态。
- `ReportCard.vue` 缩略图改固定 `aspect-ratio` + `object-fit: cover`，原图不再撑破布局。
- `App.vue` 用 `matchMedia('(max-width: 768px)')` 驱动导航折叠（「菜单」按钮 + 折叠菜单，卸载移除监听，`matchMedia` 缺失时按桌面），保留既有全部导航项与未读角标；`styles/main.css` 增加 ≤768px 媒体查询（容器内边距、表单不溢出、长文本换行、`overflow-x` 兜底）。
- 测试：前端 73（16 文件，含 HomePage 三态、App 窄屏折叠、EmptyState CTA、详情页三态）全绿 + typecheck 通过；后端 89 全绿（未受影响）。
- 附带：`.gitignore` 增加 `.vite/` 缓存目录。
