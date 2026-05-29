# M7-P5 C4.6 Scope Budget

## 1. Purpose

M7-P5 C4.6 fixes a submit-validation feedback gap found during manual verification. The labeler page correctly blocks submit when client validation fails, but the blocking path only shows a generic toast (`请先修复字段错误再提交`). For example, `详细评审意见` has `minLength: 5`; entering `批准通过` produces a valid client-side minLength error, but the user does not see the field-level reason before submit is blocked.

The chosen route is 3a: when `handleSubmitClick` blocks on existing `validatePayload` errors, trigger Formily's native validation display through `form.validate()`, show the first concrete error in the toast, and scroll/focus the first invalid field. This is a feedback-only frontend fix.

## 2. Frozen Baseline

| Anchor | Value |
|---|---|
| OpenAPI MD5 | `1acd96fb6c0fd0e7b084245d8ae3fa76` |
| Frontend Vitest | 200 |
| Migrations | 17 |
| humanpending | 162 |

C4.6 must leave OpenAPI, generated files, backend code, migrations, P5 offline-draft logic, C4.5 dataset item context, and humanpending unchanged. Frontend tests are expected to increase.

## 3. Root Cause

Two validation tracks already exist and both are correct:

- `payloadValidation.validatePayload` produces submit-blocking errors, including text minLength errors such as `最少 5 字`.
- `schemaToFormilyValidators` converts the same text minLength rule into a Formily validator with the same message.

The gap is in `LabelerSessionPage.handleSubmitClick`: it checks `validationErrors.length > 0`, shows only a generic toast, and returns. It never asks the Formily form to validate, so untouched fields may not render their native Formily errors. The field can be invalid, submit can be blocked, and the user still sees no precise field reason.

## 4. Scope

| Item | In / Out | Notes |
|---|---|---|
| Expose Formily form validate entry | In | Add `onFormReady?: (form) => void` to `SchemaFormilyRenderer` or an equivalent imperative ref. The preferred path is `onFormReady` because it avoids lifting form creation out of the renderer. |
| Trigger Formily validation on submit block | In | In `handleSubmitClick`, when `validationErrors.length > 0`, call the exposed `form.validate()` and swallow expected validation rejection. |
| Concrete toast | In | Use the first `validationErrors[0]` to show `{field label}: {reason}`, for example `详细评审意见: 最少 5 字`. |
| Scroll/focus first invalid field | In | Use the first error stableId to scroll to the field and focus the first control. Add a stable DOM anchor to `FieldFrame` if needed. |
| Preserve submit behavior | In | Passing validation still opens the existing confirmation modal. Blocking still blocks. |
| Change validation logic | Out | Do not edit `validatePayload` or `schemaToFormilyValidators`; they already produce the correct error. |
| Change submit chain | Out | Do not edit `handleConfirmSubmit`, offline submit flow, `useSubmitMutation`, server 422 mapping, autosave, or P5 offline sync. |
| Change server error mapping | Out | `serverValidationErrors` and `applyExternalErrorsToForm` remain unchanged. |
| Backend / OpenAPI / generated / migrations | Out | This is a frontend feedback-only cluster. |

## 5. Mechanism Choice

Preferred mechanism: `onFormReady?: (form: Form<Record<string, unknown>>) => void`.

Why this route:

- It keeps `createSchemaFormilyForm` inside `SchemaFormilyRenderer`, preserving the existing renderer architecture.
- It avoids external `setFieldState` injection for client errors, so Formily's validator remains the source of displayed client validation state.
- It lets `LabelerSessionPage` trigger validation without changing the real submit path.

Rejected mechanism: lifting form creation into `LabelerSessionPage`. That would widen the change surface and mix rendering internals into the submit page.

Rejected mechanism: external `setFieldState` for client errors. That fights Formily's validation lifecycle and can be overwritten by the next native validation pass.

## 6. Expected Files And Caps

### Hand-Written Cap

| Metric | Soft | Hard |
|---|---:|---:|
| Files touched | 6 | 8 |
| Net LOC | 420 | 600 |

Full-counting rule: created files count all lines; modified files count net added lines. If the soft cap is exceeded during implementation, stop and report before continuing. The hard cap is the true ceiling.

### Generated Churn

Generated churn is expected to be 0. Any generated diff is abnormal and must be reported.

### Expected Touch List

| Path | Action | Responsibility |
|---|---|---|
| `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx` | Modify | Add the Formily form exposure mechanism without changing form creation, external error application, or existing props behavior. |
| `apps/web/src/features/labeling/formily/components/FieldFrame.tsx` | Modify if needed | Add a stable field DOM anchor, such as `data-labeling-field-id`, for scroll/focus. No validation logic change. |
| `apps/web/src/pages/labeler/LabelerSessionPage.tsx` | Modify | Store the Formily form ref, trigger validation on blocking submit, show a concrete toast, and scroll/focus the first invalid field. |
| `apps/web/src/features/labeling/formily/__tests__/renderer.test.tsx` | Modify | Cover `onFormReady` form exposure and ensure existing renderer behavior remains intact. |
| `apps/web/src/pages/labeler/LabelerSessionPage.submitValidationFeedback.test.tsx` | Create or modify existing page tests | Cover minLength submit block, concrete toast text, validation display trigger, and passing-validation modal behavior. |
| Existing source/raw guard test | Modify if needed | Assert `handleConfirmSubmit`/offline submit flow/server 422 mapping are not changed. |

Implementation may combine page-level tests into an existing page test file if that keeps coverage intact and stays within the cap.

## 7. Out Of Scope

- Editing `payloadValidation.ts`, `schemaToFormilyValidators.ts`, `serverValidationErrors.ts`, or the Formily adapter semantics.
- Editing `handleConfirmSubmit`, `runLabelerSubmitWithOfflineDraft`, P5 offline draft storage, hydrate, sync, submit, autosave timing, or C4.5 dataset item card.
- Changing schema minLength values or business validation requirements.
- Adding a backend endpoint, OpenAPI shape, migration, generated type, or new validation library.
- Changing server 422 display behavior.

## 8. Frozen Checks For Implementation

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

Expected anchors: MD5 remains `1acd96fb6c0fd0e7b084245d8ae3fa76`, migrations remain 17, humanpending remains 162, generated churn remains 0, and frontend tests increase from 200.
