# LabelHub Platform

LabelHub 是一个面向数据标注流程的可信证据平台：用版本化 Schema、不可变提交事实、可追溯 AI 监督信号和可复现导出，把标注结果变成可审计的数据资产。

## 当前能力(M2+M3+M4+M5)

- Owner 端：任务创建、状态迁移、Schema Designer、Schema 版本发布、数据集 JSON/JSONL 导入、当前数据集选择、任务发布。
- Owner 端：任务 Submission 列表、历史 Renderer 只读详情、AI 检查触发、AI 检查结果 Drawer、Trusted Export 快照创建与 diff 查看。
- Labeler 端：任务广场、领取任务、Schema Renderer 作答、自动保存、提交、我的数据、历史 Submission 详情、AI provenance 只读展示。
- Reviewer 端：审核队列、Verdict filter、Submission 详情、历史 Renderer、AI provenance、Quality Ledger 审核历史、approve/reject 操作。
- 后端：JWT 认证、RBAC、Schema append-only 版本化、Dataset 导入事实、claim-time schema binding、submission-time historical render、AI provider 抽象、AI provenance append-only 写入、idempotency 重跑复用、Quality Ledger append-only 写入、实时 Verdict 派生、Trusted Export canonical artifact、AI findings 自动入 ledger。
- 数据库：MySQL 8 + Flyway V1-V8；核心事实表包括 `schema_versions`、`drafts`、`submissions`、`dataset_items`、`task_transitions`、`ai_calls`、`ai_calls_in_field`、`quality_ledger_entries`、`export_jobs`、`export_snapshots`。

## 4 个亮点 - 当前实现状态

| 亮点 | 设计位置 | 实现位置 | 状态 |
|------|----------|-------------|------|
| 1. Schema 版本化与不可变事实 | `docs/architecture/labelhub-complete-design-baseline.md` §5.4 | `SchemaService.publishVersion`、`SessionService.claimTaskItem`、`SessionService.submit`、`GET /submissions/{id}/render-schema` | ✅ 完整(M2) |
| 2. Quality Ledger + Verdict 派生视图 | baseline §8.6 | `LedgerService`、`VerdictService`、`ReviewerQueueService`、`QualityLedgerEntryMapper`、`ReviewerController`、`ReviewerQueuePage`、`ReviewerSubmissionPage` | ✅ 完整(M4 + M5 扩展) |
| 3. Trusted Export 可复现性 | baseline §9.4 | `ExportService`、`ExportArtifactBuilder`、`ExportController`、`export_snapshots`、`TrustedExportCard`、`ExportSnapshotDiffModal` | ✅ 完整(M5) |
| 4. AI Provenance + 训练污染防控 | baseline §7.7 | `AiReviewService`、`MockAiProvider`、`OpenAiCompatibleProvider`、`AiReviewController`、`AiProvenanceCard`、`AiReviewDrawer`、`LedgerService.appendAiFieldFindings` | ✅ 完整(M3 + M5-P6 真实 provider evidence) |

## 17 步 Smoke 验收

完整验收清单见 `docs/m2-acceptance-checklist.md`。

M2 结束时，数据集导入、Schema 设计、任务发布、Labeler 领取、Renderer 作答、自动保存、提交、Owner 发布 v2、历史 Submission 按 v1 渲染，均已通过浏览器和后端验证。P7b 之后，验收路径不再需要手工 SQL 插入 `dataset_items`。

M3 亮点 4 验收清单见 `docs/m3-acceptance-checklist.md`。M3 已完成 mock-backed Owner/Labeler UI evidence、AI provenance 写入、idempotency hit 复用、OpenAI-compatible provider 抽象和配置切换测试；M5-P6 已用真实 DeepSeek smoke 补齐 provider evidence。

M4 亮点 2 验收清单见 `docs/m4-acceptance-checklist.md`。M4 已完成 Reviewer queue、Quality Ledger append-only fact 写入、实时 Verdict 派生、跨身份读权限与 self-review 后端守门；M5 已扩展 AI findings 入 ledger,Reviewer assignment 和 `current_verdicts` 物化缓存仍为后续增强。

## M4 能力

- Quality Ledger append-only fact 写入(reviewer 整体 verdict)。
- 实时 Verdict 派生:`pending / approved / rejected` 从 latest ledger entry 派生,不维护物化表。
- Reviewer 全平台 submission 队列 + verdict filter + URL 同步分页。
- 跨身份读权限:Reviewer / Owner / Labeler 都能按规则读 ledger 与 verdict;self-review 由后端守门。
- Reviewer UI 完整流转:queue -> 详情(历史 schema + AI provenance + ledger) -> approve/reject -> verdict 即时更新。

详细验收路径见 `docs/m4-acceptance-checklist.md`。

## M5 能力

- Trusted Export 同步导出:Owner 对 task 创建 canonical JSON/JSONL bundle,写入 S3-compatible object storage。
- Reproducibility evidence:两次独立同参 export 产生相同 `manifestHash` / `sourceStateHash` / `fileHash`,diff endpoint 返回三层 hash 与 10 个 content file 的 SHA-256 对照。
- AI findings 自动入 Quality Ledger:`ai_field_finding` 与 `reviewer_overall_verdict` 共用 `quality_ledger_entries` fact stream。
- 真实 provider evidence:OpenAI-compatible provider 通过 DeepSeek smoke 验证,并保留 idempotency hit 复用路径。
- Browser evidence:Trusted Export diff modal、DeepSeek first call/idempotency drawer、AI/reviewer mixed ledger screenshots 已归档。

详细验收路径见 `docs/m5-acceptance-checklist.md`。

## 技术栈

- 后端：Java 17、Spring Boot 3.2、MyBatis-Plus、Flyway、OpenAPI Generator。
- 前端：React 18、TypeScript、Vite、Semi Design、TanStack Query、dnd-kit。
- DB/Infra：MySQL 8、Redis 7、MinIO。本地 M2 主要使用 MySQL；Redis/MinIO 为后续阶段保留。
- 契约：`packages/contracts/openapi/labelhub.yaml` 是 API 源头；生成代码不作为源头修改。

## 开发与本地启动

当前活跃工作区为：

```bash
/Users/gods./Downloads/LabelHub - Platform
```

不要再使用旧的 iCloud/File Provider 管理的 Desktop 路径。

启动 MySQL：

```bash
cd infra
docker compose up -d mysql
```

启动后端，默认 context path 为 `/api`：

```bash
mvn -pl services/api spring-boot:run
```

如需避开端口冲突：

```bash
LABELHUB_API_PORT=18080 mvn -pl services/api spring-boot:run
```

启动前端：

```bash
pnpm --filter @labelhub/web dev
```

如果后端运行在 `18080`：

```bash
LABELHUB_API_PROXY_TARGET=http://127.0.0.1:18080 pnpm --filter @labelhub/web dev
```

浏览器访问：

```text
http://127.0.0.1:5173
```

Demo users，密码均为 `demo1234`：

- `owner_demo`
- `labeler_demo`
- `reviewer_demo`

## 常用验证

```bash
mvn -pl services/api test
pnpm --filter @labelhub/web typecheck
pnpm --filter @labelhub/web build
bash scripts/check-protected-endpoints.sh
```

如果 Spring Boot main class 扫描受到本地 build output 污染影响，先执行：

```bash
mvn -pl services/api clean
```

项目已在 `spring-boot-maven-plugin` 中显式配置 `com.labelhub.api.ApiApplication` 作为 guardrail。

## 文档索引

- `docs/architecture/labelhub-complete-design-baseline.md`：系统完整设计基线。
- `docs/internal/decision-log.md`：M0-M2 架构与实现决策记录。
- `docs/internal/ai-coding-log.md`：Agent 实施过程和 smoke evidence 记录。
- `docs/internal/m3-startup-overview.md`：M3 启动总览和元问题草案。
- `humanpending.md`：M3-M6 待办与已解决事项。
- `docs/m2-acceptance-checklist.md`：M2 17 步验收清单。
- `docs/m3-acceptance-checklist.md`：M3 亮点 4 验收清单；真实 provider evidence 已在 M5-P6 补齐。
- `docs/m4-acceptance-checklist.md`：M4 亮点 2 Quality Ledger + Verdict 派生验收清单。
- `docs/m5-acceptance-checklist.md`：M5 Trusted Export、AI ledger integration 和真实 provider smoke 验收清单。
- `docs/screenshots/INDEX.md`：截图 evidence 索引。

## 答辩演示(亮点 1 - 90 秒)

1. Labeler 登录，进入任务广场并领取已发布 task。
2. Renderer 按 v1 schema 渲染字段，Labeler 填写、自动保存并提交。
3. Owner 登录，在 Designer 中新增字段并发布 v2。
4. Labeler 返回已提交 Submission 详情页，页面显示 `Schema 版本: v1 · 提交时绑定版本`，Renderer 仍按提交时绑定的 v1 schema 渲染。

完整截图链和 15 分钟复现路径见 `docs/m2-acceptance-checklist.md`。

## 答辩演示(亮点 4 - 90 秒)

1. Owner 打开 `/owner/tasks/{taskId}/submissions/{submissionId}`，页面显示提交时绑定 schema 的只读 Renderer。
2. Owner 点击 `AI 检查`，Drawer 显示字段级反馈、provider/model、cost/latency、input/output hash。
3. Owner 用同一 prompt 再次点击，Drawer 显示 `复用历史结果`，证明 idempotency key + input hash 命中后不再调用 provider。
4. Labeler 打开自己的 Submission 详情页，看到同一 AI provenance metadata，但没有触发入口。

完整 M3 验收路径和 M5-P6 真实 provider evidence 见 `docs/m3-acceptance-checklist.md` 与 `docs/m5-acceptance-checklist.md`。

## 答辩演示(亮点 2 - 90 秒)

1. Reviewer 登录,进入 `/reviewer/submissions`,队列显示 pending Verdict tag。
2. Reviewer 打开某个 submission 详情页,页面同时显示历史 schema、AI provenance 和 Ledger 历史。
3. 点击 Approve,Header Verdict tag 立刻变绿,Ledger 新增一条 `reviewer_overall_verdict` fact。
4. 再点击 Reject,Header Verdict tag 变红,Ledger 新增第二条 fact;Verdict 来自 latest ledger entry 实时派生。

完整 M4 验收路径和 SQL/Service/HTTP/UI evidence 链见 `docs/m4-acceptance-checklist.md`。

## 答辩演示(亮点 3 - 90 秒)

1. Owner 打开 task 详情页的 Trusted Export Card,点击 `导出` 两次得到两个 snapshot。
2. 勾选两个 snapshot,点击 `对比所选`。
3. Diff Modal 显示绿色 Banner、三层 hash 全部一致、10 个 content file SHA-256 全部一致。
4. 总结:Trusted Export 是 source facts 的 canonical 函数,不是可变 DB 快照。

完整 M5 验收路径和截图链见 `docs/m5-acceptance-checklist.md`。

## 答辩演示(亮点 2/4 整合 - 90 秒)

1. Owner 触发真实 AI review,系统写入 `ai_calls`、`ai_calls_in_field` 和 `quality_ledger_entries.ai_field_finding`。
2. 同 prompt 再触发,Drawer 显示 `复用历史结果`,ledger 不重复追加。
3. Reviewer 打开 submission 详情,Ledger 同时显示蓝色 AI entry 与 reviewer verdict entry。
4. DB evidence 显示 `quality_ledger_entries` 中 `ai_field_finding` 与 `reviewer_overall_verdict` 共存,`current_verdicts` 仍为 0 rows。

完整 M5 验收路径和 DB/UI evidence 链见 `docs/m5-acceptance-checklist.md`。

## Project Shape

- `apps/web`：React + TypeScript 前端。
- `services/api`：Spring Boot 业务权威边界。
- `services/agent`：后续 AI Worker 进程。
- `packages/contracts`：OpenAPI 契约源头。
- `docs`：架构、决策、验收与截图文档。
- `infra`：本地开发基础设施。
- `submission`：答辩提交材料索引。
