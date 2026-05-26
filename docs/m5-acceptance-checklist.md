# M5 验收 Checklist:Trusted Export + AI Ledger Integration + Real Provider Evidence

> M5 完成 9 个月项目最后两条工程证据建立:亮点 3 Trusted Export 可复现性,以及亮点 2/4 跨亮点整合。M5-P6 同时补齐真实 OpenAI-compatible provider smoke,使 4 个亮点全部具备可答辩 evidence 链。

## M5 Phase 总览

| Phase | 主题 | 评估 | 完成判据 |
|-------|------|------|----------|
| M5-P0 | 战略 + 12 元问题 + 物理 schema 核对 | 100/100 | V1 unique key 冲突暴露 + S1=A / S2=A / S3=C' / S4=B / S5=A 裁决 |
| M5-P1 | V8 + OpenAPI 0.8.0 + MinIO 接入 | 100/100 | 移除 `file_hash` unique key + 5 新列 + S3Client fail-fast + mapper append-only |
| M5-P2 | ExportService + canonical artifact + reproducibility 单测 | 100/100 | 两次独立 export 同 hash + 22 次 S3 PUT + runtime 排除边界 |
| M5-P3a | Controller + Security + diff + Testcontainers MinIO | 100/100 | 4 endpoints + diff endpoint + Owner-only RBAC + Docker-disabled integration evidence |
| M5-P3b | Owner UI Trusted Export Card | 100/100 | checkbox 选 2 + Diff Modal 三层 hash + 10 file matches |
| M5-P4 | AI findings 入 Quality Ledger Service | 100/100 | OpenAPI 0.9.0 + `appendAiFieldFindings` + idempotency hit 不重复写 ledger |
| M5-P5 | AI findings ledger 集成测试 + UI 最小增强 | 100/100 | 6 集成测试 + mixed AI/reviewer ledger UI |
| M5-P6 | Real DeepSeek smoke + D 口径补完 | 100/100 | 9 张截图 + DB 4 表 evidence + `downloadCount=0` live-smoke fix |

## 亮点 3:Trusted Export 可复现性

核心命题:Trusted Export 不是 DB 快照,而是 source facts 的 canonical 函数。两次独立同参 export 应产生相同可信训练数据集 hash,且不能复用缓存冒充可复现。

### Evidence 1:契约

- OpenAPI 0.8.0 重塑 export 同步契约。
- 端点:
  - `POST /tasks/{taskId}/exports`
  - `GET /tasks/{taskId}/exports`
  - `GET /exports/snapshots/{snapshotId}`
  - `GET /exports/snapshots/{snapshotId}/diff?compareWith=...`
- Schemas:
  - `ExportSnapshot`
  - `ExportFileEntry`
  - `ExportSnapshotDiff`
  - `PagedExportSnapshots`
- 安全边界:SecurityConfig `OWNER` role + Service task ownership guard + cross-owner 404 防枚举。

### Evidence 2:物理 Schema

- V8 migration 移除 `uk_export_snapshots_file_hash` unique key。
- 原因:可复现性要求同 source state 的两次独立 export 可以插入两条不同 snapshot row,且 hash 相同。
- V8 新增:
  - `manifest_hash`
  - `source_state_hash`
  - `object_key`
  - `file_manifest`
  - `record_counts`
- `file_hash` 保留为 V1 aggregate hash,改普通索引以支持 hash lookup。

### Evidence 3:Service 单测

| 测试 | 证明 |
|------|------|
| `two_independent_exports_for_same_task_produce_identical_hash` | 两次独立 export 的 `manifestHash` / `sourceStateHash` / `fileHash` 一致;jobId/snapshotId/generatedAt/objectKey 不同;S3 PUT 调用 22 次 |
| `manifest_hash_excludes_runtime_metadata` | runtime metadata 改变不影响 content hash |
| `export_hash_changes_when_new_ledger_entry_appended` | source facts 变化后三层 hash 全变 |
| `export_includes_derived_verdict_snapshot` | `verdicts.jsonl` 物化导出时刻的派生 verdict,但不写 `derivedAt` |
| `export_rejects_when_task_not_owned_by_requester` | Service 层 ownership guard 生效 |

### Evidence 4:HTTP 集成测试

| 测试 | 证明 |
|------|------|
| `two_independent_http_exports_produce_identical_hash` | 真 HTTP POST 两次,Testcontainers MinIO 中出现两套 11 objects,三层 hash 一致 |
| `diff_endpoint_returns_equal_for_two_identical_exports` | diff endpoint 返回 `equal=true`,三层 hash 全 match,10 file-level matches 全 match |
| `diff_endpoint_returns_not_equal_when_source_state_differs` | source state 改变后 diff 不再 equal |
| Export RBAC / cross-owner tests | Labeler/Reviewer 403,cross-owner snapshot 404 |
| `ExportControllerValidationContractTest` | 预防 generated controller `@Min/@Max/@NotNull` annotation drift |

### Evidence 5:UI 浏览器证据

| 截图 | 证明 |
|------|------|
| `phase-m5p3b-trusted-export-empty.png` | Trusted Export Card 初始空状态 |
| `phase-m5p3b-trusted-export-one-snapshot.png` | 第一次导出后出现 snapshot |
| `phase-m5p3b-trusted-export-two-selected.png` | 两个 snapshot 被选中,对比按钮启用 |
| `phase-m5p3b-diff-modal-equal.png` | Diff Modal 绿色 Banner + 三层 hash 全一致 + 10 file matches 全一致 |

### 90 秒演示路径

1. Owner 打开 task 详情页,定位 Trusted Export Card。
2. 点击 `导出` 两次,得到两个独立 snapshot。
3. 勾选两个 snapshot,点击 `对比所选`。
4. 展示 Diff Modal:绿色 equal Banner、File/Manifest/Source State 三层 hash 全一致、10 个 content file SHA-256 全一致。
5. 总结:同 source facts -> 同 canonical artifact hash;不是缓存复用,因为后端单测和 HTTP 集成测试都证明两次独立写入。

## 亮点 2/4 跨亮点整合

核心命题:AI 检查不只产生 `ai_calls` provenance fact,还会自动进入 Quality Ledger 成为可审计 fact。AI 和 reviewer 共用一条 append-only ledger fact stream,但 entry type 和 actor type 区分来源。

### Evidence 1:契约扩展

- OpenAPI 0.9.0 增加 `QualityLedgerEntryType.ai_field_finding`。
- 新增 `AiFieldFindingPayload`:
  - `fieldPath`
  - `stableId`
  - `label`
  - `severity`
  - `finding`
  - `confidence`
- `QualityLedgerEntryPayload` 改为 oneOf:
  - `ReviewerOverallVerdictPayload`
  - `AiFieldFindingPayload`
- `actorUserId` 改 nullable,因为 `actorType=ai` 时没有 human actor。
- `CreateLedgerEntryRequest` 保持 reviewer-only,防止客户端伪造 AI evidence。

### Evidence 2:物理 Schema

- `quality_ledger_entries` 表无需新 migration。
- M4 已预留:
  - `evidence_type`
  - `actor_type`
  - `actor_id`
  - `ai_call_id`
  - `payload`
- M5 只是开始写第二种 `evidence_type=ai_field_finding`。
- 这证明 M4 的 ledger schema 是可扩展 fact stream,不是只为 reviewer verdict 写死的表。

### Evidence 3:Service 单测

| 测试 | 证明 |
|------|------|
| `ai_field_findings_appended_to_ledger_on_new_review` | 新 AI provider call 后调用 `LedgerService.appendAiFieldFindings` |
| `review_returns_existing_result_when_idempotency_key_matches` | idempotency hit 时 provider/ai_call/field rows/ledger append 全部 never |
| `appendAiFieldFindings_writes_one_ledger_entry_per_finding` | 每个 finding 写一条 ledger row,字段精确 |
| `appendAiFieldFindings_with_empty_list_writes_nothing` | 空 findings 不污染 ledger |
| `appendAiFieldFindings_optional_fields_omitted_when_null` | payload optional 字段不写 null 噪音 |

### Evidence 4:HTTP 集成测试

| 测试 | 证明 |
|------|------|
| `ai_review_appends_field_findings_to_ledger_on_new_call` | Owner POST AI review 后 ledger 出现 N 条 `ai_field_finding` |
| `repeat_ai_review_does_not_duplicate_ledger_entries` | 同 prompt 第二次 POST 命中 idempotency,provider call count 不变,ledger count 不复增 |
| `reviewer_can_read_ai_field_finding_entries` | Reviewer 可读 AI findings ledger |
| `owner_can_read_ai_field_finding_entries` | Owner 可读 AI findings ledger |
| `labeler_can_read_ai_field_finding_entries_for_own_submission` | Submission Labeler 可读自己的 AI findings ledger |
| `cross_labeler_cannot_read_ai_field_findings_returns_404` | 其他 Labeler 不能枚举或读取 |

### Evidence 5:UI + DB 证据

| 截图 | 证明 |
|------|------|
| `phase-m5p6-db-ai-ledger-evidence.png` | DB 4 表 evidence:`ai_calls`、`ai_calls_in_field`、`quality_ledger_entries` 两种 entry type、`current_verdicts` 0 rows |
| `phase-m5p5-reviewer-ledger-mixed.png` | Reviewer ledger 看到 AI entry |
| `phase-m5p5-reviewer-ledger-mixed-after-approve.png` | AI entry + reviewer verdict entry 共存,顶部 Verdict 来源链回 reviewer ledger row |

### 90 秒演示路径

1. 展示 DB evidence:真实 AI call 进入 `ai_calls`,field finding 进入 `ai_calls_in_field`。
2. 同时展示 `quality_ledger_entries` 有 `ai_field_finding` 和 `reviewer_overall_verdict` 两种 entry type。
3. 展示 Reviewer UI:蓝色 AI entry 与 reviewer verdict entry 共存。
4. 展示 idempotency hit evidence:重复 AI review 不复调 provider,ledger 不复增。
5. 总结:AI 与 reviewer 是同一 append-only Quality Ledger fact stream,不是两套不可合流的数据。

## 亮点 4 真实 Provider Evidence

核心命题:M3 的 OpenAI-compatible provider 抽象不是 mock-only 设计;M5-P6 用真实 DeepSeek provider 验证配置驱动切换、真实 cost/latency、idempotency hit 和持久化 fact 链。

### Evidence 1:真实调用

- 本地运行选择 OpenAI-compatible provider。
- Provider metadata 显示 `deepseek / deepseek-v4-flash`。
- `phase-m5p6-deepseek-first-call.png` 显示:
  - provider/model
  - nonzero cost
  - real latency
  - persisted hashes
  - field-level findings
- API key 不进入截图、日志或仓库文件。

### Evidence 2:Idempotency 真实运行

- `phase-m5p6-deepseek-idempotency-hit.png` 显示同 prompt 重触发后复用历史结果。
- Drawer Banner 表达 input hash 与 idempotency key 命中,本次没有再次调用 provider。
- `phase-m5p6-db-ai-ledger-evidence.png` 证明真实调用写入 DB,且 AI finding 同步进入 Quality Ledger。

### 90 秒演示路径

1. 打开 Owner submission AI Drawer first-call 截图,指出真实 provider/model、cost、latency。
2. 打开 idempotency-hit 截图,指出 repeat prompt 没有再次调用 provider。
3. 打开 DB evidence 截图,指出 `ai_calls` + `ai_calls_in_field` + `quality_ledger_entries` 三层事实链。
4. 总结:M3 provider abstraction 在 M5 通过真实 provider 验证,且不需要新业务代码。

## M5 工作流里程碑

1. **S3=C' 修正 reproducibility 定义**:M5-P0 把"重新生成同一 snapshot"修正为"两次独立同参 export 比较 hash",避免 self-referential evidence。
2. **物理 schema 先行**:V1 `file_hash` unique key 与 reproducibility 冲突在 P0 被发现,P1 用 V8 修正。
3. **annotation parity 从修补到预防**:M5-P3a 主动加 `ExportControllerValidationContractTest`,不再等 live smoke 暴露。
4. **contract correction 可逆且透明**:M5-P4 将 `actorUserId` 改 nullable,承认 M4 单一 reviewer entry 假设与 M5 AI entry 冲突。
5. **live smoke 修真实 bug**:M5-P6 暴露 `export_jobs.download_count` NOT NULL gap,当场初始化为 0 并编译通过。
6. **D 口径闭环**:P3b/P5 截图、P6 真实 provider smoke 都在 M5-P6 一次性补完。

## 透明记录

这些项不阻塞 M5 evidence,但建议 M6 答辩前 polish:

- AI Drawer 成功文案仍出现 "Mock provider" 字样,真实 provider metadata 已正确显示 DeepSeek。
- M5-P6 Trusted Export 使用的 task 在导出时没有 still-`submitted` submission,因此 snapshot record count 为 0;hash reproducibility 仍成立,但 final-defense 前可用非零 records task 重拍更饱满。

## M5+ 后续增强

- Owner/Labeler ledger history card。
- AI finding aggregation card 和 entry-type filter。
- Reviewer accept/reject individual AI finding workflow。
- AI Call id 点击打开 provenance/trace。
- Export download endpoint / signed URL。
- Export retention and failed-object cleanup policy。
- Async export job and progress polling。
- Provider retry/backoff、streaming、usage-token cost accounting。
- Optional `current_verdicts` materialized cache after real volume appears。

## 4 个亮点最终状态

| 亮点 | 状态 | 答辩核心 evidence |
|------|------|-------------------|
| 1. Schema 版本化 + 不可变事实 | ✅ 完整(M2) | Historical Renderer 按提交时 schema version 渲染 |
| 2. Quality Ledger + Verdict 派生 | ✅ 完整(M4 + M5 扩展) | Ledger append-only fact stream + Verdict derived view + AI/reviewer entries 共存 |
| 3. Trusted Export 可复现性 | ✅ 完整(M5) | Diff Modal equal + 三层 hash + 10 file matches |
| 4. AI Provenance + 训练污染防控 | ✅ 完整(M3 + M5-P6 real provider) | DeepSeek first call + idempotency hit + DB fact chain |

