# LabelHub API 清单（整理版）

> 来源：`packages/contracts/openapi/labelhub.yaml`（v0.10.0，契约唯一源头，ADR-012）
> 权限来源：`services/api/.../config/SecurityConfig.java` + 各 Controller 的 `@PreAuthorize` 注解
> 整理日期：2026-06-10。共 **89 个 operation**、15 个 tag、151 个 component schema。

## 1. 总体约定

- **生产 Base URL**：`http://120.26.182.61:8443/api`（公网 HTTP + IP + 8443,外部访问使用此地址）
- **本地 Base URL**：`http://localhost:8080/api`（context path 为 `/api`,仅本机开发/测试使用）
- **鉴权**：全局 `bearerAuth`（JWT Bearer Token）；仅 `/auth/*` 四个端点公开。
  Refresh token 走 `labelhub_refresh` HttpOnly Cookie，**不接受请求体传递**。
- **内部接口**：`/internal/**` 在 Spring Security 层 `permitAll`，实际由 `InternalTokenFilter` 校验 `X-Internal-Token` 请求头，仅供 `services/agent` Worker 回调。
- **自定义动作命名**：非 CRUD 动作用 `:action` 后缀（如 `:activate`、`:test-connection`、`:bulk-update`），其余用子资源路径（如 `/claim`、`/submit-drafts`、`/resolve`）。
- **鉴权分层**：粗粒度角色在 `SecurityConfig`（按路径前缀/方法），细粒度归属校验（如"只能看自己的 submission"）在服务层基于 `JwtPrincipal` 判断。

### 角色模型

| 角色 | 说明 |
|------|------|
| `OWNER` | 任务方：建任务、数据集、Schema、AI 预审、导出 |
| `LABELER` | 标注员：领取、草稿、提交（注册默认角色） |
| `REVIEWER` | 初审员：全量初审队列、approve/reject、标记疑难 |
| `SENIOR_REVIEWER` | 高级审核：仲裁工作台（`senior_review_cases`） |
| `PLATFORM_ADMIN` | 平台管理：LLM Provider、用户/角色、审计、计量仪表盘 |
| `AI_AGENT` | 仅内部，不可通过任何端点授予 |

---

## 2. 认证 Auth（4 个，公开）

| 方法 | 路径 | operationId | 说明 |
|------|------|-------------|------|
| POST | `/auth/login` | `login` | 登录，签发 access token + HttpOnly refresh cookie |
| POST | `/auth/register` | `register` | 注册，**固定创建 LABELER**，请求中任何角色字段被服务端忽略 |
| POST | `/auth/refresh` | `refresh` | 用 HttpOnly cookie 刷新 access token |
| POST | `/auth/logout` | `logout` | 吊销 refresh token 并清除 cookie |

## 3. 任务 Tasks(17 个)

**Owner 管理面（13 个，`OWNER`）：**

| 方法 | 路径 | operationId | 说明 |
|------|------|-------------|------|
| GET | `/tasks` | `listTasks` | 任务分页列表 |
| POST | `/tasks` | `createTask` | 创建任务（配额由数据集题目数派生，不手填） |
| GET | `/tasks/{taskId}` | `getTask` | 任务详情 |
| PATCH | `/tasks/{taskId}` | `updateTask` | 更新任务 |
| DELETE | `/tasks/{taskId}` | `deleteTask` | **永久删除**任务及全部任务域事实数据（sessions/submissions/ai_calls/ledger/exports/transitions） |
| PATCH | `/tasks/{taskId}/transition` | `transitionTask` | 状态迁移（draft→published→…），写入 `task_transitions` 事实 |
| GET | `/tasks/{taskId}/transitions` | `getTaskTransitions` | 状态迁移历史 |
| GET | `/tasks/{taskId}/workflow-progress` | `getTaskWorkflowProgress` | 工作流进度看板 |
| GET | `/tasks/{taskId}/submissions` | `listOwnerTaskSubmissions` | Owner 视角的提交列表 |
| PATCH | `/tasks/{taskId}/current-dataset` | `updateTaskCurrentDataset` | 绑定/切换当前数据集 |
| POST | `/tasks/{taskId}/schema-from-template` | `applySchemaTemplateToTask` | 从模板库套用 Schema |
| GET | `/tasks/{taskId}/ai-prereview/summary` | `getTaskAiPrereviewSummary` | AI 预审汇总 |
| POST | `/tasks/{taskId}/ai-prereview/enqueue` | `enqueueTaskAiPrereviews` | 批量入队 AI 预审（写 outbox） |

**Labeler 领取面（4 个，`LABELER`）：**

| 方法 | 路径 | operationId | 说明 |
|------|------|-------------|------|
| GET | `/tasks/marketplace` | `listMarketplaceTasks` | 任务广场（可领取任务） |
| POST | `/tasks/{taskId}/claim` | `claimTaskItem` | 领取单题（FCFS + 乐观锁，ADR-007；领取时绑定 Schema 版本） |
| POST | `/tasks/{taskId}/claim-batch` | `claimTaskItems` | 批量领取 |
| POST | `/tasks/{taskId}/submit-drafts` | `submitTaskDrafts` | 本批次批量提交（当前会话用请求体，其余用各自最新草稿） |

## 4. 数据集 Datasets（5 个，`OWNER`）

| 方法 | 路径 | operationId | 说明 |
|------|------|-------------|------|
| GET | `/datasets` | `listDatasets` | 数据集列表 |
| POST | `/datasets` | `uploadDataset` | 上传导入（multipart，JSON/JSONL） |
| GET | `/datasets/{datasetId}` | `getDataset` | 数据集详情 |
| GET | `/datasets/{datasetId}/items` | `listDatasetItems` | 数据项分页 |
| PATCH | `/datasets/{datasetId}/items:bulk-update` | `bulkUpdateDatasetItems` | 批量更新数据项 |

## 5. 标注 Schema（9 个，`OWNER`）

| 方法 | 路径 | operationId | 说明 |
|------|------|-------------|------|
| GET | `/schemas` | `listSchemas` | Schema 列表 |
| POST | `/schemas` | `createSchema` | 创建 Schema |
| POST | `/schemas/import` | `importSchemaTemplate` | 导入模板 |
| GET | `/schemas/{schemaId}` | `getSchema` | Schema 详情 |
| DELETE | `/schemas/{schemaId}` | `archiveSchemaTemplate` | 归档**模板库** Schema（任务绑定的 Schema 不走此端点） |
| GET | `/schemas/{schemaId}/versions` | `listSchemaVersions` | 版本列表 |
| POST | `/schemas/{schemaId}/versions` | `publishSchemaVersion` | 发布新版本（append-only，发布后不可变，ADR-002） |
| GET | `/schemas/{schemaId}/versions/{versionId}` | `getSchemaVersion` | 版本详情 |
| GET | `/schemas/{schemaId}/versions/{versionId}/export` | `exportSchemaVersionPackage` | 导出版本包 |

## 6. 标注会话 Sessions（7 个，`LABELER`）

| 方法 | 路径 | operationId | 说明 |
|------|------|-------------|------|
| GET | `/my/sessions` | `listMySessions` | 我的会话列表 |
| GET | `/sessions/{sessionId}` | `getSession` | 会话详情（含领取时绑定的 Schema 版本） |
| GET | `/sessions/{sessionId}/draft` | `getSessionDraft` | 最新草稿 |
| PUT | `/sessions/{sessionId}/draft` | `saveDraft` | 保存草稿（append-only `drafts` 表，每次新 revision；前端应 3–5s 节流） |
| POST | `/sessions/{sessionId}/submit` | `submitSession` | 提交终稿（提交后会话转 `submitted` 不可改） |
| POST | `/sessions/{sessionId}/attachments` | `uploadSessionAttachment` | 上传字段附件（multipart） |
| GET | `/sessions/{sessionId}/attachments/{attachmentRef}` | `downloadSessionAttachment` | 下载附件（LABELER/OWNER/REVIEWER/SENIOR_REVIEWER 四角色可见性各自校验） |

## 7. 提交 Submissions（2 个，登录 + 服务层归属校验）

| 方法 | 路径 | operationId | 说明 |
|------|------|-------------|------|
| GET | `/submissions/{submissionId}` | `getSubmission` | 提交详情（标注员本人可见） |
| GET | `/submissions/{submissionId}/render-schema` | `getSubmissionRenderSchema` | 历史渲染：提交时绑定的 Schema + 答案（任务 Owner 或提交者可读） |

## 8. 质量与审核 Reviews（9 个）

| 方法 | 路径 | operationId | 权限 | 说明 |
|------|------|-------------|------|------|
| GET | `/reviewer/submissions` | `listReviewerQueue` | REVIEWER / SENIOR_REVIEWER | 初审队列（审核员共享池，按设计如此） |
| POST | `/reviews/batch` | `batchReviewSubmissions` | REVIEWER / SENIOR_REVIEWER | 批量复核（每项独立事务，失败不回滚整批） |
| GET | `/submissions/{id}/ledger-entries` | `listSubmissionLedger` | 登录 | Quality Ledger 历史（append-only，ADR-003） |
| POST | `/submissions/{id}/ledger-entries` | `createLedgerEntry` | REVIEWER / SENIOR_REVIEWER | 写入审核结论（approve/reject 只追加不更新） |
| GET | `/submissions/{id}/verdict` | `getSubmissionVerdict` | 登录 | Verdict 实时派生自 latest ledger entry（无物化表） |
| POST | `/submissions/{id}/review-difficulty` | `markSubmissionReviewDifficulty` | REVIEWER | 标记疑难，升级为高审 case |
| GET | `/senior-review/cases` | `listSeniorReviewCases` | SENIOR_REVIEWER | 仲裁工作台 case 列表（AI 升级 / 疑难标记 / 抽检） |
| POST | `/senior-review/cases/{caseId}/resolve` | `resolveSeniorReviewCase` | SENIOR_REVIEWER | 仲裁裁决 |
| POST | `/adjudication-rules/{ruleId}/recompute` | `recomputeAdjudicationRule` | — | **占位端点，实现返回 501 Not Implemented（M4 未实现）** |

## 9. AI 预审 AIReview（8 个）+ Prompt 版本（1 个）

| 方法 | 路径 | operationId | 权限 | 说明 |
|------|------|-------------|------|------|
| GET | `/ai-review/rules?taskId=` | `listAiReviewRules` | 登录（服务层校验） | 任务的三区阈值规则列表 |
| POST | `/ai-review/rules` | `saveAiReviewRule` | 登录（服务层校验） | 保存规则草稿 |
| POST | `/ai-review/rules/{ruleId}/publish` | `publishAiReviewRule` | 登录（服务层校验） | 发布规则 |
| POST | `/ai-review/field-assist` | `createFieldAssistCall` | 登录（服务层校验） | 标注时单字段 AI 辅助调用 |
| GET | `/submissions/{id}/ai-review` | `getSubmissionAiProvenance` | 登录 | AI 预审 provenance（prompt 版本/模型/hash/cost/latency，ADR-005） |
| POST | `/submissions/{id}/ai-review` | `triggerSubmissionAiReview` | OWNER | 同步触发单条 AI 预审 |
| POST | `/submissions/{id}/ai-prereview/enqueue` | `enqueueSubmissionAiPrereview` | OWNER | 单条入队（异步，写 outbox） |
| GET | `/submissions/{id}/ai-trace` | `getSubmissionAiTrace` | 登录 | AI 调用全链路 trace |
| GET | `/prompt-versions/default` | `getDefaultPromptVersion` | 登录 | 当前默认 prompt 版本 |

## 10. 可信导出 Exports（8 个，`OWNER` 为主）

| 方法 | 路径 | operationId | 说明 |
|------|------|-------------|------|
| GET | `/tasks/{taskId}/exports` | `listTaskExports` | 任务导出快照列表 |
| POST | `/tasks/{taskId}/exports` | `createTaskExport` | 创建导出作业（canonical 产物 + 多训练格式：表格快照 / OpenAI 微调 / TRL SFT / TRL 偏好） |
| GET | `/tasks/{taskId}/export-fields` | `getTaskExportFields` | 可绑定导出字段目录（含样例值、覆盖率、格式推荐） |
| GET | `/exports/snapshots/{snapshotId}` | `getExportSnapshot` | 快照详情 |
| POST | `/exports/snapshots/{snapshotId}/archive` | `archiveExportSnapshot` | 归档快照 |
| GET | `/exports/snapshots/{snapshotId}/files/{fileName}` | `downloadExportSnapshotFile` | 下载单文件（服务层按 principal 校验） |
| GET | `/exports/snapshots/{snapshotId}/packages/{packageType}` | `downloadExportSnapshotPackage` | 下载格式包（服务层按 principal 校验） |
| GET | `/exports/snapshots/{snapshotId}/diff` | `diffExportSnapshots` | 快照间 diff |

## 11. LLM Provider 配置（8 个，`PLATFORM_ADMIN`）

| 方法 | 路径 | operationId | 说明 |
|------|------|-------------|------|
| GET | `/llm/providers` | `listLlmProviders` | 配置列表 |
| POST | `/llm/providers` | `createLlmProvider` | 创建（密钥经 `LABELHUB_LLM_PROVIDER_MASTER_KEY` 加密落库） |
| GET | `/llm/providers/{id}` | `getLlmProvider` | 详情 |
| PATCH | `/llm/providers/{id}` | `updateLlmProvider` | 更新 |
| DELETE | `/llm/providers/{id}` | `deleteLlmProvider` | 删除 |
| POST | `/llm/providers/{id}:activate` | `activateLlmProvider` | 原子激活（同时禁用其他启用项；必须已有密钥） |
| POST | `/llm/providers/{id}:test-connection` | `testLlmProvider` | 测试已保存配置（可临时覆盖密钥） |
| POST | `/llm/providers:test-connection` | `testUnsavedLlmProvider` | 测试未保存配置 |

## 12. 平台管理（10 个，`PLATFORM_ADMIN`）

| 方法 | 路径 | operationId | 说明 |
|------|------|-------------|------|
| GET | `/users` | `listUsers` | 用户列表（不含密码哈希） |
| DELETE | `/users/{userId}` | `deleteUser` | 软删除（保留历史证据引用） |
| POST | `/users/{userId}/roles` | `grantUserRole` | 旧版角色调整（仅 LABELER/REVIEWER/SENIOR_REVIEWER） |
| POST | `/platform/users/{userId}/roles` | `grantPlatformUserRole` | 平台角色授予（含 OWNER；PLATFORM_ADMIN/AI_AGENT 不可授；不可自授） |
| GET | `/platform/cost-metrics` | `getPlatformCostMetrics` | AI token 与成本事实（只读 `ai_calls`，不重算价格） |
| GET | `/platform/labor-metrics` | `getPlatformLaborMetrics` | 人力计数（提交/审核/三类返工信号，不读答案内容） |
| GET | `/platform/efficiency-metrics` | `getPlatformEfficiencyMetrics` | token 复用与单条数据成本 |
| GET | `/audit-logs` | `listAuditLogs` | 审计日志查询（payload hash 防篡改） |
| GET | `/audit-logs/export.csv` | `exportAuditLogs` | 审计日志 CSV 导出（已做公式注入防护） |

## 13. 内部接口 Internal（仅 `X-Internal-Token`，供 services/agent 回调）

| 方法 | 路径 | operationId | 说明 |
|------|------|-------------|------|
| GET | `/internal/ai-review/submissions/{id}/context` | `getAiReviewContext` | Worker 拉取 AI 预审上下文 |
| POST | `/internal/ai-review/results` | `reportAiReviewResult` | Worker 回写结构化预审结果（function-calling 强制，ADR-006） |
| POST | `/internal/exports/jobs/{exportJobId}/run` | —（**契约缺失**） | Worker 执行导出作业。实现见 `InternalExportController`，但未登记进 `labelhub.yaml` |

---

## 14. 契约与实现差异（整理时发现）

1. **`POST /internal/exports/jobs/{exportJobId}/run` 未入契约**：实现存在且被 `WebClientExportApiClient` 使用，但 OpenAPI YAML 中没有对应 operation——与 Internal tag 中已登记的 2 个 AI 预审内部端点不一致，违反 ADR-015 漂移控制的精神。
2. **`POST /adjudication-rules/{ruleId}/recompute` 是占位**：契约中存在，实现固定抛 `501 Not Implemented`（注释标注 M4 未实现）。
3. **README 统计漂移**：README 写 "87 operations"，契约实际 89 个；README 写 "Flyway ×29"，迁移目录实际 30 个文件。
