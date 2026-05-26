# M4 验收清单(亮点 2:Quality Ledger + 实时 Verdict 派生)

> 本清单用于 M4 验收和答辩演示复现。完整跑通约 8 分钟。

## 准备

- 浏览器已登录 `reviewer_demo` / `labeler_demo` / `owner_demo` 三个账号。
- 后端启动,默认 mock provider(M3-P6 模式)。
- M2 / M3 已有 submission 数据(P6b 17 步 smoke 已留存 evidence)。

## 4 项必达 Smoke

### 必达 1:Reviewer 进入审核队列

| 步骤 | 操作 | 验证 |
|------|------|------|
| 1.1 | `reviewer_demo` 登录 | Sidebar 显示 "审核队列" |
| 1.2 | 点击 "审核队列" -> `/reviewer/submissions` | Table 显示 submitted submissions |
| 1.3 | 查看列表 Verdict tag | 初始 pending 为橙色,三态颜色正确 |
| 1.4 | 添加 `?verdict=pending` URL filter | 列表只显示 pending submission,刷新后 filter 保留 |

### 必达 2:Reviewer 写入 ledger entry

| 步骤 | 操作 | 验证 |
|------|------|------|
| 2.1 | 点击列表 "开始审核" | URL = `/reviewer/submissions/:id`,不嵌套 taskId |
| 2.2 | 查看详情页 | 历史 Schema Renderer + AI Provenance Card + Ledger Card 三层视图共存 |
| 2.3 | 输入 reason `schema 完整,数据干净` | textarea 可编辑 |
| 2.4 | 点击 Approve 绿色按钮 | Toast "已通过" + Header Verdict tag 立刻变绿 "已通过" + Ledger Card 新增 1 行 |
| 2.5 | 可选 DB 验证 | `quality_ledger_entries` 有 1 行,`evidence_type='reviewer_overall_verdict'`,`actor_id=1003`,`payload.verdict='approve'` |

### 必达 3:Verdict 实时派生(亮点 2 核心 evidence)

| 步骤 | 操作 | 验证 |
|------|------|------|
| 3.1 | 同 submission 点击 Reject 红色按钮 | Toast "已拒绝" + Verdict tag 变红 "已拒绝" + Ledger Card 增至 2 行 |
| 3.2 | DB 验证 `quality_ledger_entries` 行数 | 2 行,append-only,不是 update |
| 3.3 | DB 验证 `current_verdicts` 表 | 无 M4 写入;M4 不维护物化表,纯派生 |
| 3.4 | 浏览器刷新详情页 | Verdict tag 仍为 "已拒绝",由 latest ledger entry 派生 |
| 3.5 | 返回 `/reviewer/submissions` queue | 同 submission verdict = `rejected` |
| 3.6 | 改 URL 为 `?verdict=approved` | 该 submission 不出现 |
| 3.7 | 改 URL 为 `?verdict=rejected` | 该 submission 出现 |

### 必达 4:权限边界(可信性 evidence)

| 步骤 | 操作 | 验证 |
|------|------|------|
| 4.1 | `owner_demo` 访问 `/reviewer/submissions` | RequireRole 403 forbidden 页面 |
| 4.2 | `labeler_demo` 访问 `/reviewer/submissions/:id` | RequireRole 403 forbidden 页面 |
| 4.3 | `reviewer_demo` 进入 `labeler_demo` 提交的 submission 详情 | render-schema / AI provenance / ledger 全部可读 |
| 4.4 | M5 待补:多角色用户自审 | 后端 409 `SELF_REVIEW_NOT_ALLOWED` + UI Toast "不能审核自己提交的内容" |

## 亮点 2 答辩演示路径

### 短演示(90 秒)

1. Reviewer queue 显示 pending Verdict tag。
2. 点击 Approve,Header Verdict tag 即时变绿,Ledger 新增一行。
3. 点击 Reject,Header Verdict tag 即时变红,Ledger 新增第二行。
4. 总结: **Verdict 是从 append-only Quality Ledger 实时派生的视图,不是一个独立的可变状态。**

### 完整演示(5 分钟)

完整演示跑 4 项必达,再展示 SQL 层:

- `quality_ledger_entries` 表中的 append-only fact row。
- `current_verdicts` 表在 M4 不写入,证明当前 Verdict 是实时派生。
- `GET /submissions/{submissionId}/verdict` 返回 `derivedFromEntryId`,链回具体 ledger entry。

## 工程证据 4 层(亮点 2 工程价值)

| 层 | Evidence | 文件 |
|----|----------|------|
| SQL contract | `latest_reviewer_verdict_query_tie_breaks_by_id_desc` | `QualityLedgerEntryMapperContractTest.java` |
| Service 单测 | `new_ledger_entry_changes_verdict` | `VerdictServiceTest.java` |
| HTTP 集成 | `new_ledger_entry_changes_verdict_via_http` | `QualityLedgerIntegrationTest.java` |
| UI smoke | `phase-m4p4-reviewer-approve.png` / `phase-m4p4-reviewer-reject.png` | `docs/screenshots/` |

## 截图位置

完整截图清单见 `docs/screenshots/INDEX.md` 的 M4 段。
