# M2 验收清单(17 步 Smoke)

> 本清单用于 M2 验收和答辩演示复现。完整跑通约 15-20 分钟，目标是全程浏览器操作，不依赖手工 SQL 插入 `dataset_items`。

## 准备

- MySQL 8 已启动，Flyway 已迁移到 V1-V7。
- API 已启动，默认端口 `18080`，context path `/api`。
- Web 已启动，默认端口 `5173`。
- Demo users 可登录：`owner_demo`、`labeler_demo`、`reviewer_demo`，密码均为 `demo1234`。
- 浏览器建议使用干净 session，避免旧 token 或旧路由状态影响验收。

## 步骤 1-6: Owner 端准备

| 步骤 | 操作 | 验证 | 截图 |
|------|------|------|------|
| 1 | `owner_demo` 登录 | Sidebar 显示 `任务管理` / `Schema 管理` | `phase5b1-login.png` / `phase5b1-owner-placeholder.png` |
| 2 | 创建 task | task 列表出现新草稿，详情页可访问 | `phase5b2-create-modal.png` / `phase5b2-list-after-create.png` |
| 3 | 进入 Schema Designer | 两栏 Designer shell 渲染 | `phase-m2p4a-designer-shell.png` |
| 4 | 添加字段并发布 v1 | Designer 支持 7 类字段；发布后当前版本更新 | `phase-m2p4b-seven-types.png` / `phase-m2p4c-publish-modal.png` / `phase-m2p4c-publish-success.png` |
| 5 | task 详情上传 JSONL dataset 并设为当前 | 上传成功，列表显示 5 条；当前 dataset tag 更新 | `phase-m2p7b-upload-success-list.png` / `phase-m2p7b-set-current-success.png` |
| 6 | 发布 task | task 状态进入 `published` / `发布中`，transition 记录出现 | `phase5c-after-publish.png` / `phase5c-final-timeline.png` |

## 步骤 7-14: Labeler 作答闭环

| 步骤 | 操作 | 验证 | 截图 |
|------|------|------|------|
| 7 | `labeler_demo` 登录 | Sidebar 显示 `任务广场` / `我的数据` | 无单独截图；在任务广场截图中体现 |
| 8 | 进入任务广场 | 看到步骤 6 发布的 task | `phase-m2p6b-marketplace.png` |
| 9 | 点击领取 | 跳转 `/labeler/sessions/{id}`，session 状态为 claimed | `phase-m2p6b-session-initial.png` |
| 10 | Renderer 渲染 v1 字段 | 页面按领取时绑定的 schema version 渲染字段 | `phase-m2p6b-session-initial.png` |
| 11 | 填写字段并等待 autosave | Autosave tag 进入已保存状态 | `phase-m2p6b-session-autosaved.png` |
| 12 | 刷新页面 | 字段值从 latest draft 恢复 | 动态验证，无单独截图 |
| 13 | 故意留空 required 字段并提交 | Toast warning，submit Modal 不打开 | `phase-m2p6b-validation-blocked.png` |
| 14 | 修复字段并提交 | Submit Modal 确认后创建 submission 并跳转 submission route | `phase-m2p6b-submit-modal-filled.png` / `phase-m2p6b-submit-success-routing.png` |

## 步骤 15-17: 亮点 1 历史 Schema Evidence

| 步骤 | 操作 | 验证 | 截图 |
|------|------|------|------|
| 15 | 进入 `我的数据` | 列表显示 claimed/submitted sessions | `phase-m2p6c-my-sessions.png` |
| 16 | Owner 回到 Designer，新增字段并发布 v2 | task 当前 schema version 指针更新到 v2 | `phase-m2p6c-owner-designer-v2.png` |
| 17 | Labeler 返回 `/labeler/submissions/{id}` | Submission 详情显示 `Schema 版本: v1 · 提交时绑定版本`，Renderer 仍按 v1 渲染 | `phase-m2p6c-historical-render-after-v2.png` |

## 跨角色 / 跨身份防御

| 场景 | 期望 | 截图 |
|------|------|------|
| Owner 访问 `/labeler/**` | 前端 `RequireRole(['LABELER'])` 渲染 403，不进入后端业务请求 | `phase-m2p6c-owner-labeler-403.png` |
| Labeler A 访问 Labeler B 的 submission | 后端返回 404，页面显示 `Submission 不存在或无权访问` | `phase-m2p6c-cross-labeler-404.png` |
| 重复提交同一 session | 后端返回 409 `SESSION_ALREADY_SUBMITTED` | P5c HTTP smoke，未单独截图 |
| 已发布 task 切换 dataset | UI 禁用 `设为当前`；后端 PATCH 返回 409 `TASK_PUBLISHED_LOCK` | `phase-m2p7b-published-disabled.png` |

## 后端契约验证(可选)

亮点 1 的后端证据链来自 P3/P5c/P6c：

- `GET /submissions/{submissionId}/render-schema` 返回提交时绑定的 schema version。
- P5c smoke 中，submission `schemaVersionId` 继承 session claim-time `schemaVersionId`，不会读取 task 当前 pointer。
- Owner 发布 v2 后，DB 显示 task 当前 pointer 已更新，而旧 submission 仍绑定 v1。
- P6c UI 使用 render-schema 端点，不读取 task 当前 schema。

可选 curl 检查路径：

```bash
curl -H "Authorization: Bearer <labeler-token>" \
  http://127.0.0.1:18080/api/submissions/<submissionId>/render-schema
```

期望响应中 `schemaVersion.versionNumber` 仍为提交时版本，例如 `1`。

## 数据集导入边界验证

| 场景 | 期望 | 截图 |
|------|------|------|
| JSONL 上传成功 | Toast `已上传 dataset,5 条数据`，列表新增 dataset | `phase-m2p7b-upload-success-list.png` |
| 空数组 `[]` | Toast `数据集为空,请检查文件内容` | `phase-m2p7b-empty-dataset-toast.png` |
| 非法 JSONL | Toast 显示后端 line number message | `phase-m2p7b-invalid-jsonl-toast.png` |
| 已发布 task 上传 dataset | 上传仍成功，因为上传不改 task pointer | `phase-m2p7b-published-disabled.png` |

## 答辩演示路径

- 短演示(亮点 1,约 90 秒)：步骤 9-11 + 步骤 16-17。
- 完整演示(约 15 分钟)：步骤 1-17。
- 防御演示(约 2 分钟)：Owner 访问 Labeler 路由 403、跨 Labeler submission 404、已发布 task 禁用 dataset switch。

## 截图索引

完整截图清单见 `docs/screenshots/INDEX.md`。
