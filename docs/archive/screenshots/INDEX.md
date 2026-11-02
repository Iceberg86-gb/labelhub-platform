# Screenshot Evidence Index

> 本索引只引用 `docs/screenshots/` 下已存在的截图，不重新生成图片。

## M1 / Shell / Owner Task

- `phase5a-layout-console.png` — M1 shell and Vite proxy console evidence.
- `phase5b1-login.png` — Login page.
- `phase5b1-wrong-password.png` — Wrong-password inline error.
- `phase5b1-owner-placeholder.png` — Owner authenticated shell placeholder.
- `phase5b1-forbidden.png` — Role guard forbidden page.
- `phase5b2-create-modal.png` — Owner create task modal.
- `phase5b2-field-errors.png` — Create task field errors.
- `phase5b2-deadline-error.png` — Deadline validation.
- `phase5b2-list-with-data.png` — Task list with records.
- `phase5b2-list-after-create.png` — Task list after create.
- `phase5b2-filter-published.png` — Published filter.
- `phase5b2-empty-filter-paused.png` — Empty paused filter.
- `phase5c-detail-initial.png` — Task detail initial state.
- `phase5c-transition-modal.png` — Transition reason modal.
- `phase5c-reason-required.png` — Required reason validation.
- `phase5c-reason-trim-required.png` — Whitespace reason validation.
- `phase5c-after-publish.png` — Task after publish.
- `phase5c-final-timeline.png` — Transition timeline.
- `phase5c-list-ended.png` — Task list after ended transition.
- `phase5c-publish-guard-quota.png` — Publish guard error.

## M2-P4a Schema Designer Foundation

- `phase-m2p4a-designer-shell.png` — Schema Designer two-column shell.
- `phase-m2p4a-labeler-forbidden.png` — Labeler forbidden from Owner schema route.

## M2-P4b Field Editors

- `phase-m2p4b-top-field-picker.png` — Top-level field picker.
- `phase-m2p4b-seven-types.png` — Seven M2 field types in Designer.
- `phase-m2p4b-child-picker.png` — Nested-object child picker.
- `phase-m2p4b-designer-errors.png` — Designer validation errors.
- `phase-m2p4b-json-preview.png` — Read-only JSON preview.

## M2-P4c Publish And Version History

- `phase-m2p4c-publish-modal.png` — Publish confirmation modal.
- `phase-m2p4c-publish-success.png` — Publish success and current version update.
- `phase-m2p4c-duplicate-banner.png` — Duplicate schema content conflict.
- `phase-m2p4c-validation-block.png` — Validation blocks publish.
- `phase-m2p4c-version-history.png` — Version history SideSheet.
- `phase-m2p4c-version-json-v1-v3.png` — v1/v3 JSON evidence.

## M2-P5c Historical Render Backend Evidence

- `phase-m2p5c-render-schema-historical-v1.png` — Backend historical render-schema evidence after v2 publish.

## M2-P6b Live Labeling Flow

- `phase-m2p6b-marketplace.png` — Labeler marketplace.
- `phase-m2p6b-session-initial.png` — Claimed session initial renderer.
- `phase-m2p6b-session-autosaved.png` — Autosave success state.
- `phase-m2p6b-autosave-failure.png` — Autosave failure state.
- `phase-m2p6b-validation-blocked.png` — Required-field validation blocks submit.
- `phase-m2p6b-submit-modal.png` — Submit modal.
- `phase-m2p6b-submit-modal-filled.png` — Submit modal with filled answer summary.
- `phase-m2p6b-submit-success-routing.png` — Submit success routing.

## M2-P6c Submission History

- `phase-m2p6c-my-sessions.png` — Labeler "我的数据" list.
- `phase-m2p6c-submission-v1-before-v2.png` — Submission v1 before Owner publishes v2.
- `phase-m2p6c-owner-designer-v2.png` — Owner Designer after publishing v2.
- `phase-m2p6c-historical-render-after-v2.png` — Submission still renders historical v1 after v2.
- `phase-m2p6c-owner-labeler-403.png` — Owner blocked from Labeler route.
- `phase-m2p6c-cross-labeler-404.png` — Cross-labeler submission access returns not-found UI.

## M2-P7b Dataset Upload

- `phase-m2p7b-initial-dataset-card.png` — Dataset card initial state from in-app browser.
- `phase-m2p7b-chrome-initial-dataset-card.png` — Dataset card initial state in Chrome smoke.
- `phase-m2p7b-upload-success-list.png` — JSONL upload success and dataset list.
- `phase-m2p7b-set-current-success.png` — Current dataset set successfully.
- `phase-m2p7b-invalid-jsonl-toast.png` — Invalid JSONL error toast.
- `phase-m2p7b-empty-dataset-toast.png` — Empty dataset error toast.
- `phase-m2p7b-published-disabled.png` — Published task allows upload but disables set-current.

## M3-P4 Owner AI Review UI

- `phase-m3p4-task-submissions-table.png` — Owner task detail submission table.
- `phase-m3p4-owner-submission-empty-provenance.png` — Owner submission detail before AI provenance exists.
- `phase-m3p4-ai-drawer-first.png` — First AI review trigger with `idempotencyHit=false`.
- `phase-m3p4-ai-drawer-idempotency-hit.png` — Repeat AI review trigger reuses persisted evidence with `idempotencyHit=true`.
- `phase-m3p4-labeler-owner-route-403.png` — Labeler blocked from Owner submission route by frontend role guard.

## M3-P5 Labeler AI Provenance

- `phase-m3p5-labeler-ai-provenance.png` — Labeler submission detail with historical Renderer and shared AI provenance card.
- `phase-m3p5-owner-shared-provenance.png` — Owner submission detail still renders the shared AI provenance card.

## M3-P6 OpenAI-Compatible Provider

- P6 实施通用 OpenAI-compatible provider、配置切换和单元测试。
- 真实 provider smoke 已在 M5-P6 用 DeepSeek 补齐；见本页 M5-P6 段。

## M4 - Quality Ledger + Reviewer UI

### M4-P4 — Reviewer UI 完整流程(亮点 2 UI 层 evidence)

| 截图 | 演示价值 |
|------|----------|
| `phase-m4p4-reviewer-queue.png` | Reviewer 审核队列 + Verdict tag 三态 |
| `phase-m4p4-reviewer-detail-pending.png` | Reviewer submission 详情,空 ledger,verdict=pending |
| `phase-m4p4-reviewer-approve.png` | **点击 Approve -> Verdict tag 立刻变绿 + Ledger 新增一行(亮点 2 实时派生 evidence)** |
| `phase-m4p4-reviewer-reject.png` | 再点 Reject -> Verdict tag 变红 + Ledger 第二行 |
| `phase-m4p4-reviewer-filter-approved.png` | URL filter `?verdict=approved` 工作 |
| `phase-m4p4-reviewer-forbidden-owner.png` | Owner 访问 `/reviewer/**` -> 跨身份 403 |

## M5 - Trusted Export + AI Ledger Integration

### M5-P3b — Trusted Export Owner UI(亮点 3 浏览器层 evidence,M5-P6 补)

| 截图 | 演示价值 |
|------|----------|
| `phase-m5p3b-trusted-export-empty.png` | Trusted Export Card 初始空状态 |
| `phase-m5p3b-trusted-export-one-snapshot.png` | 第一次导出后 Card 显示 1 snapshot |
| `phase-m5p3b-trusted-export-two-selected.png` | 两次导出 + 勾选两个 snapshot |
| `phase-m5p3b-diff-modal-equal.png` | **Diff Modal equal Banner + 三层 hash + 10 file matches(亮点 3 答辩核心)** |

### M5-P5 — AI Ledger UI 共存(亮点 2/4 整合 evidence,M5-P6 补)

| 截图 | 演示价值 |
|------|----------|
| `phase-m5p5-reviewer-ledger-mixed.png` | Reviewer ledger 列表显示 AI `ai_field_finding` entry(浅蓝 AI 视觉处理) |
| `phase-m5p5-reviewer-ledger-mixed-after-approve.png` | **AI + reviewer entries 共存,两种 `entryType` 一眼区分(亮点 2/4 整合)** |

### M5-P6 — 真实 DeepSeek Smoke(亮点 4 evidence 补全)

| 截图 | 演示价值 |
|------|----------|
| `phase-m5p6-deepseek-first-call.png` | 真实 DeepSeek 调用 Drawer(provider/model/cost/latency/fieldFindings) |
| `phase-m5p6-deepseek-idempotency-hit.png` | **同 prompt 重触发 -> 复用历史结果(亮点 4 + 亮点 2 整合 idempotency 信任边界)** |
| `phase-m5p6-db-ai-ledger-evidence.png` | **DB 层 4 表 fact evidence(`ai_calls` + `ai_calls_in_field` + `quality_ledger_entries` + `current_verdicts` 不维护)** |

## 亮点 1 答辩 Evidence 链

- Contract endpoint: `GET /submissions/{submissionId}/render-schema` in OpenAPI 0.4.2+.
- Backend evidence: `phase-m2p5c-render-schema-historical-v1.png`.
- Designer version evidence: `phase-m2p4c-version-json-v1-v3.png`.
- UI list evidence: `phase-m2p6c-my-sessions.png`.
- Owner current pointer evidence: `phase-m2p6c-owner-designer-v2.png`.
- Final UI evidence: `phase-m2p6c-historical-render-after-v2.png`.

## 亮点 4 答辩 Evidence 链

- Contract endpoints: `POST /submissions/{submissionId}/ai-review` and `GET /submissions/{submissionId}/ai-review` in OpenAPI 0.6.0+.
- Owner discovery: `phase-m3p4-task-submissions-table.png`.
- First AI review trigger: `phase-m3p4-ai-drawer-first.png`.
- Idempotency hit reuse: `phase-m3p4-ai-drawer-idempotency-hit.png`.
- Owner provenance card: `phase-m3p5-owner-shared-provenance.png`.
- Labeler transparency card: `phase-m3p5-labeler-ai-provenance.png`.
- Real provider smoke: `phase-m5p6-deepseek-first-call.png`.
- Real provider idempotency hit: `phase-m5p6-deepseek-idempotency-hit.png`.

## 亮点 2 答辩 Evidence 链

- Contract endpoints: `/reviewer/submissions`, `/submissions/{submissionId}/ledger-entries`, and `/submissions/{submissionId}/verdict` in OpenAPI 0.7.0+.
- Reviewer queue: `phase-m4p4-reviewer-queue.png`.
- Pending derived verdict before ledger fact: `phase-m4p4-reviewer-detail-pending.png`.
- Append approve ledger fact and derive approved verdict: `phase-m4p4-reviewer-approve.png`.
- Append reject ledger fact and derive rejected verdict: `phase-m4p4-reviewer-reject.png`.
- Verdict filter evidence: `phase-m4p4-reviewer-filter-approved.png`.

## 亮点 3 答辩 Evidence 链

- Contract endpoints: `POST /tasks/{taskId}/exports` and `GET /exports/snapshots/{snapshotId}/diff` in OpenAPI 0.8.0+.
- Service evidence: `two_independent_exports_for_same_task_produce_identical_hash` proves two independent exports get identical hashes while writing every object again.
- HTTP integration evidence: `two_independent_http_exports_produce_identical_hash` proves the same path through Testcontainers MinIO.
- UI evidence: `phase-m5p3b-diff-modal-equal.png`.

## 亮点 2/4 整合 Evidence 链

- Contract: `QualityLedgerEntryType` includes `ai_field_finding` in OpenAPI 0.9.0+.
- Service evidence: `ai_field_findings_appended_to_ledger_on_new_review` plus idempotency-hit `verify never`.
- HTTP integration evidence: `repeat_ai_review_does_not_duplicate_ledger_entries`.
- DB evidence: `phase-m5p6-db-ai-ledger-evidence.png`.
- UI evidence: `phase-m5p5-reviewer-ledger-mixed-after-approve.png`.

## 4 个亮点 90 秒演示路径

### 亮点 1:Schema 版本化 + 不可变事实

1. `phase-m2p6c-submission-v1-before-v2.png`:Labeler submission 绑定 v1。
2. `phase-m2p6c-owner-designer-v2.png`:Owner 发布 v2。
3. `phase-m2p6c-historical-render-after-v2.png`:旧 submission 仍按 v1 渲染。
4. 结论:提交事实绑定 schema version,不是跟随 current schema 漂移。

### 亮点 2:Quality Ledger + Verdict 派生

1. `phase-m4p4-reviewer-detail-pending.png`:无 reviewer ledger fact 时 Verdict 为 pending。
2. `phase-m4p4-reviewer-approve.png`:Approve append fact 后 Verdict 变 approved。
3. `phase-m4p4-reviewer-reject.png`:Reject append 第二条 fact 后 latest fact 派生 rejected。
4. 结论:Verdict 是 append-only ledger 的实时派生 view,不是独立可变状态。

### 亮点 3:Trusted Export 可复现性

1. `phase-m5p3b-trusted-export-one-snapshot.png`:第一次 export 产生 snapshot。
2. `phase-m5p3b-trusted-export-two-selected.png`:第二次独立 export 后勾选两个 snapshot。
3. `phase-m5p3b-diff-modal-equal.png`:Diff Modal 显示 equal、三层 hash 全一致、10 file matches 全一致。
4. 结论:Trusted Export 是 source facts 的 canonical 函数。

### 亮点 4:AI Provenance + 训练污染防控

1. `phase-m5p6-deepseek-first-call.png`:真实 DeepSeek call 产生 provider/model/cost/latency/finding evidence。
2. `phase-m5p6-deepseek-idempotency-hit.png`:同 prompt 重触发复用历史结果。
3. `phase-m5p6-db-ai-ledger-evidence.png`:DB 显示 AI provenance facts 和 Quality Ledger facts。
4. 结论:AI evidence 可追溯、可复用、可审计,且不会因重复触发污染 ledger。

## 防御 Evidence

- Cross-role Owner -> Labeler 403: `phase-m2p6c-owner-labeler-403.png`.
- Cross-labeler submission 404: `phase-m2p6c-cross-labeler-404.png`.
- Published task dataset switch disabled: `phase-m2p7b-published-disabled.png`.
- Cross-role Owner -> Reviewer 403: `phase-m4p4-reviewer-forbidden-owner.png`.
