# M7-P5 C4.5 Pre-Estimate

## 1. Summary

C4.5 adds a labeler-facing "待评审内容" card to the session workspace. It fixes the current blind spot where a labeler can see the schema form but cannot see the dataset item being evaluated. The card uses the frozen claim snapshot as the primary payload source and falls back to the live dataset item only for legacy or exceptional sessions.

Frozen anchors for C4.5:

| Anchor | Value |
|---|---|
| OpenAPI MD5 | `1acd96fb6c0fd0e7b084245d8ae3fa76` |
| Frontend Vitest | 194 after old C5 |
| Migrations | 17 |
| humanpending | 161 after old C5 |

## 2. File Plan

| File | Action | Estimate | Responsibility |
|---|---|---:|---|
| `apps/web/src/features/labeling/DatasetItemContextCard.tsx` | Create | 180 | Defensive item payload rendering: known QA-ish fields, unknown folded/raw fallback, no-crash empty state. |
| `apps/web/src/features/labeling/DatasetItemContextCard.test.tsx` | Create | 210 | Component tests for known fields, unknown scalar/object fallback, all-generic payload, malformed payload, and no unsafe assumptions. |
| `apps/web/src/pages/labeler/LabelerSessionPage.tsx` | Modify | +35 | Select primary/fallback item payload and mount the context card above the answer form without touching submit/offline logic. |
| `apps/web/src/pages/labeler/LabelerSessionPage.datasetItemContext.test.tsx` | Create if needed | 70 | Source-priority regression: claim snapshot wins over live dataset item; fallback works when snapshot payload is absent. |
| Existing scoped CSS file | Modify if needed | +25 | Wrapping for long prompt/model answer/reference and raw JSON; responsive spacing. |

Estimated net LOC: about 450 if page-level tests are split out; about 380 if source-priority tests fit in the component test. Cap: soft 500 / hard 700, files soft 5 / hard 7. Generated churn remains 0.

## 3. Component Interface

Preferred interface:

```ts
type DatasetItemContextCardProps = {
  itemPayload: unknown;
  sourceLabel?: 'claimSnapshot' | 'datasetItem' | 'none';
};
```

The component must treat `itemPayload` as unknown:

- if it is a plain object, inspect known keys defensively;
- if it is a scalar, array, null, or otherwise malformed, render a generic raw fallback or empty-state without throwing;
- never mutate payload or derive answer defaults from it;
- never render `content_markdown` as unsafe HTML.

Helper functions may live in the same file unless tests or readability justify a small `datasetItemContext.ts` helper. Do not introduce a broad data-model abstraction in C4.5.

## 4. Source Selection

`LabelerSessionPage` should compute the payload source near the existing `detail`/`fields` derivation:

1. primary: `detail.session.claimSnapshot?.datasetItemPayload`;
2. fallback: `detail.datasetItem.itemPayload`;
3. no payload: pass `undefined` or `null` and let the card render an empty-state.

Because generated `claimSnapshot` is an optional free-form object, access must use optional chaining and runtime guards. Tests should prove that when both sources differ, the frozen claim snapshot wins.

Rationale: the claim snapshot is the labeler's audit source. The live dataset item may drift later and must not silently change what the labeler is understood to have evaluated.

## 5. Rendering Rules

Known fields get first-class presentation:

| Key | Display | Notes |
|---|---|---|
| `prompt` | 问题 | Prominent block at top. |
| `model_answer` | 模型回答 | Main answer block. |
| `reference` | 参考答案 | Reference block, distinct from model answer. |
| `tags` | 标签 | Array values as tags; scalar fallback as text. |
| `difficulty` | 难度 | Metadata chip/text. |
| `category` | 分类 | Metadata chip/text. |
| `media_url` | 媒体链接 | Safe link for non-empty string. |
| `media_type` | 媒体类型 | Metadata chip/text. |
| `content_markdown` | 补充内容 | Plain/preformatted text. |

Unknown fields remain visible in a raw data area:

- scalar values: key-value rows;
- arrays/objects: pretty JSON;
- known fields already shown structurally should not be duplicated in raw data unless the implementation chooses an explicit "show full raw payload" toggle;
- if no known field is present, the raw fallback becomes the main body so the card is still useful for arbitrary datasets.

## 6. Mounting Plan

Mount the card in `LabelerSessionPage` between the header/actions block and the `SchemaFormilyRenderer` card.

The mount must not change:

- `answerPayload` state;
- `handleAnswerPayloadChange`;
- offline draft hydration/buffer/sync;
- submit sequence;
- P3a/P3b validation and server error mapping.

The card is read-only context for the labeler. It is not part of the answer payload.

## 7. C5 Redo Obligation

The old `docs/internal/m7p5-verification.md` and associated humanpending closure were produced before this card existed. C4.5 must explicitly be followed by a new C5 closure. That new C5 must:

- include C4.5 in the P5 cluster map;
- update the frontend Vitest close value from the C4.5 result;
- include dataset item context in manual/browser or D-port end-to-end verification;
- add an append-only humanpending follow-up or final closure note that records the repaired labeler context display.

C4.5 does not edit verification or humanpending. It only records that the existing C5 is no longer the final P5 closure once this fix lands.

## 8. Testing Plan

Required coverage:

- known-field rendering for `prompt`, `model_answer`, `reference`, `tags`, `difficulty`, and `category`;
- `media_url` renders as a safe link only when it is a string;
- `content_markdown` renders as text, not unsafe HTML;
- unknown scalar fields appear in raw fallback;
- unknown object/array fields appear as pretty JSON;
- payload with no known fields still renders generic key-value/JSON content;
- malformed payload (`null`, scalar, array) does not throw;
- claim snapshot source wins over live dataset item fallback;
- live dataset item fallback is used only when snapshot payload is absent;
- card does not call answer payload change handlers or write into `answerPayload`;
- existing offline draft and submit tests continue to pass.

## 9. Three-Viewport Strategy

The card should use normal page/card width and scoped wrapping:

- 1440: structured question/model/reference blocks can be stacked or two-section; readability is preferred over dense columns.
- 1280: metadata chips and actions wrap without horizontal overflow.
- 1024: long prompt/model answer/reference and raw JSON wrap or scroll inside the card; the form remains below the context card.

No global layout CSS should be changed. Browser verification may remain D-port until the new C5, but C4.5 implementation should include component coverage and avoid fixed widths that can overflow.

## 10. Risks And STOP Conditions

- If rendering requires backend, OpenAPI, generated type, or migration changes, STOP.
- If the page cannot access `claimSnapshot.datasetItemPayload` safely without changing generated types, STOP.
- If implementing the card requires changing P5 offline draft store/hydrate/sync/submit logic, STOP.
- If arbitrary payload rendering would require unsafe HTML or a new markdown renderer, do not add it in C4.5; use plain text/preformatted fallback.
- If component LOC or tests exceed soft cap, stop and report rather than hiding complexity in `LabelerSessionPage`.

## 11. Caps

| Metric | Soft | Hard |
|---|---:|---:|
| Hand-written files touched | 5 | 7 |
| Hand-written net LOC | 500 | 700 |
| Generated files | 0 expected | 0 expected |

Cap accounting uses full-counting for created files and net additions for modified files. Generated churn must remain 0.

## 12. Frozen Checks

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

Expected anchors: MD5 `1acd96fb6c0fd0e7b084245d8ae3fa76`, migrations 17, humanpending 161, generated churn 0, frontend tests increase from 194.

