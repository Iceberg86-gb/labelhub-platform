# M7-P5 C4.5 Scope Budget

## 1. Purpose

M7-P5 C4.5 fixes a production-path blind spot found during manual validation: the labeler session page renders the answer form but does not render the dataset item that the labeler is supposed to evaluate. The API already returns the item payload through the session claim snapshot and the live dataset item. C4.5 adds a read-only dataset item context card above the answer form, using the frozen claim snapshot as the primary source.

This is an inserted repair cluster, like prior probe-driven fixes. It must complete before P5 can be closed again.

## 2. Frozen Baseline

| Anchor | Value |
|---|---|
| OpenAPI MD5 | `1acd96fb6c0fd0e7b084245d8ae3fa76` |
| Frontend Vitest | 194 after old C5 |
| Migrations | 17 |
| humanpending | 161 after old C5 |

C4.5 must leave OpenAPI, generated files, backend code, migrations, P5 offline-draft logic, and humanpending unchanged. Frontend tests are expected to increase.

## 3. C5 Redo Obligation

The old P5 C5 verification was written against the page before dataset item context existed. After C4.5, that closure is no longer authoritative. When C4.5 passes, P5 C5 must be rerun and rewritten so that:

- the end-to-end labeler flow includes the dataset item context card;
- `docs/internal/m7p5-verification.md` records C4.5 in the cluster map and invariants;
- the `[M7-P5 resolved]` humanpending item is updated through an append-only follow-up entry or a new closure entry according to the final audit process;
- frontend test anchors are recalculated from the C4.5 result.

C4.5 itself does not modify humanpending or verification. It records this obligation so P5 does not remain in an inconsistent "old C5 plus new repair" state.

## 4. Scope

| Item | In / Out | Notes |
|---|---|---|
| Dataset item context card | In | New read-only component, for example `DatasetItemContextCard`, accepting an unknown/free-form item payload. |
| Frozen snapshot source | In | Primary source is `detail.session.claimSnapshot?.datasetItemPayload`; `detail.datasetItem.itemPayload` is legacy/exception fallback only. |
| Known field display | In | Structure common QA fields (`prompt`, `model_answer`, `reference`, `tags`, `difficulty`, `category`, `media_url`, `media_type`, `content_markdown`) when present. |
| Unknown fallback | In | Unknown fields are visible in a folded/secondary raw data area; scalar values use key-value display, arrays/objects use pretty JSON. |
| Full generic fallback | In | If no known fields exist, show a generic key-value/JSON payload view so the labeler can still inspect the item. |
| LabelerSessionPage mount | In | Mount between the session header and `SchemaFormilyRenderer`. |
| Three viewport behavior | In | 1440/1280/1024 layout must avoid horizontal overflow and keep long text/JSON readable. |
| Offline draft layer | Out | Do not modify C1-C4 store, hydrate, sync, submit, autosave, or P3a/P3b validation paths. |
| Backend / OpenAPI / generated / migrations | Out | Data is already present in the session detail response. No contract change. |
| Editable sample data | Out | The card is read-only and never writes into `answerPayload`. |

## 5. Data Source Rule

| Priority | Source | Purpose |
|---:|---|---|
| 1 | `detail.session.claimSnapshot?.datasetItemPayload` | Frozen claim-time payload. This is the audit source and the default render source. |
| 2 | `detail.datasetItem.itemPayload` | Fallback for legacy or malformed sessions where the claim snapshot lacks item payload. |
| 3 | Empty/unsupported | Render an empty-state card explaining that no item payload is available; do not crash. |

The primary source follows the same audit logic as session-bound schema versioning: a labeler should evaluate the item as it existed when the session was claimed.

## 6. Field Rendering Rules

| Field | Label | Rendering |
|---|---|---|
| `prompt` | 问题 | Main text block. |
| `model_answer` | 模型回答 | Main text block, visually distinct from reference. |
| `reference` | 参考答案 | Secondary/reference block. |
| `tags` | 标签 | Tag list if array-like; string fallback if scalar. |
| `difficulty` | 难度 | Metadata chip/text. |
| `category` | 分类 | Metadata chip/text. |
| `media_url` | 媒体链接 | Link when it is a non-empty string. |
| `media_type` | 媒体类型 | Metadata text/chip. |
| `content_markdown` | 补充内容 | Render as plain/preformatted text, not unsafe HTML. |
| Unknown scalar fields | Raw data | Key-value row in a folded or secondary area. |
| Unknown object/array fields | Raw data | Pretty JSON in a folded or secondary area. |

If no known fields are present, the card still renders the payload through the generic raw fallback. No payload shape may cause a white screen.

## 7. Expected Files And Caps

### Hand-Written Cap

| Metric | Soft | Hard |
|---|---:|---:|
| Files touched | 5 | 7 |
| Net LOC | 500 | 700 |

Full-counting rule: created files count all lines; modified files count net added lines. If the soft cap is exceeded during implementation, stop and report before continuing. The hard cap is the true ceiling.

### Generated Churn

Generated churn is expected to be 0. Any generated diff is abnormal and must be reported.

### Expected Touch List

| Path | Action | Responsibility |
|---|---|---|
| `apps/web/src/features/labeling/DatasetItemContextCard.tsx` | Create | Defensive rendering of known item fields and generic fallback for unknown payloads. |
| `apps/web/src/features/labeling/DatasetItemContextCard.test.tsx` | Create | Known-field, fallback, malformed shape, and source-priority tests. |
| `apps/web/src/pages/labeler/LabelerSessionPage.tsx` | Modify | Select frozen snapshot payload first, live dataset payload as fallback, and mount card above form. |
| `apps/web/src/pages/labeler/LabelerSessionPage.datasetItemContext.test.tsx` | Create if needed | Page-level source-priority and "does not write answerPayload" regression coverage. |
| Existing scoped CSS file | Modify if needed | Minimal styles for long text/JSON wrapping and responsive card layout. |

Implementation may combine page-level assertions into the component test if it still proves frozen-source priority and mount behavior without weakening coverage.

## 8. Out Of Scope

- Changing session detail API shape, OpenAPI, generated types, or backend claim snapshot construction.
- Changing `SchemaFormilyRenderer`, answer validation, submit flow, autosave, offline draft storage, sync, or submit integration.
- Rendering dataset item fields as editable answer fields.
- Inferring answer defaults from dataset item payload.
- Adding markdown HTML rendering or unsafe media embedding.
- Completing or rewriting P5 C5 in this cluster.

## 9. Frozen Checks For Implementation

Implementation report must include:

```bash
md5 -q packages/contracts/openapi/labelhub.yaml
find services/api/src/main/resources/db/migration -name 'V*.sql' | wc -l
grep -cE "^- \[" humanpending.md
pnpm --filter @labelhub/web typecheck
pnpm --filter @labelhub/web test
git diff --stat
git diff --check
```

Expected anchors: MD5 remains `1acd96fb6c0fd0e7b084245d8ae3fa76`, migrations remain 17, humanpending remains 161, generated churn remains 0, and frontend tests increase from 194.

