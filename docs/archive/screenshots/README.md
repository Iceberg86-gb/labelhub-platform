# Demo Screenshot Index

当前截图用于 M1 资产审计与后续答辩材料整理。每张图都对应一个可见 contract，不补 fake 页面。

- `phase5a-layout-console.png`: 前端 Shell 与 `/api/actuator/health` 代理验证，证明 Vite proxy 与 Layout 已接通。
- `phase5b1-login.png`: 登录页，覆盖 JWT 认证入口与 Semi Form。
- `phase5b1-wrong-password.png`: 错密码字段级错误，覆盖 `/login` 401 白名单 middleware。
- `phase5b1-forbidden.png`: Labeler 访问 Owner 路由被拒绝，覆盖 `RequireRole`。
- `phase5b1-owner-placeholder.png`: Owner 登录后的占位首页，覆盖 authenticated Header 与 Owner 路由。
- `phase5b2-list-with-data.png`: 任务列表带数据，覆盖 URL 同步分页与状态筛选入口。
- `phase5b2-create-modal.png`: 创建任务 Modal，覆盖 Owner 创建任务表单。
- `phase5b2-field-errors.png`: 字段级错误高亮，覆盖前端与后端 fieldErrors 映射。
- `phase5b2-list-after-create.png`: 创建成功后列表刷新，覆盖 TanStack Query invalidation。
- `phase5b2-filter-published.png`: `status=published` URL 筛选，覆盖 lowercase OpenAPI enum 查询。
- `phase5b2-empty-filter-paused.png`: `status=paused` 空状态，覆盖筛选后的 Empty 状态。
- `phase5b2-deadline-error.png`: 过去截止时间校验，覆盖 deadline 前端字段验证。
- `phase5c-detail-initial.png`: 任务详情初始态，覆盖 detail query 与状态徽章。
- `phase5c-reason-required.png`: 状态迁移 reason 必填校验。
- `phase5c-reason-trim-required.png`: 纯空格 reason 被拒绝，覆盖 trim 校验。
- `phase5c-transition-modal.png`: 共享状态迁移 Modal，覆盖 reason 审计输入。
- `phase5c-after-publish.png`: 发布后详情页，覆盖状态变更与 Timeline 首条记录。
- `phase5c-final-timeline.png`: 四步状态迁移完成后的 Timeline，覆盖 append-only `task_transitions` 可视化。
- `phase5c-list-ended.png`: 回到列表后状态徽章同步为已结束，覆盖列表 query invalidation。
- `phase5c-publish-guard-quota.png`: publish guard 错误在 Modal 内持久显示，覆盖 400 + `fieldErrors` 映射。
