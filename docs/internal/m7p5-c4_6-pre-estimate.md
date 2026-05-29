# M7-P5 C4.6 Pre-Estimate

## 1. Summary

C4.6 improves submit validation feedback on the labeler session page. It does not change what is valid or invalid. It only makes already-computed validation errors visible and actionable when submit is blocked before the confirmation modal opens.

Frozen anchors:

| Anchor | Value |
|---|---|
| OpenAPI MD5 | `1acd96fb6c0fd0e7b084245d8ae3fa76` |
| Frontend Vitest | 200 |
| Migrations | 17 |
| humanpending | 162 |

## 2. File Plan

| File | Action | Estimate | Responsibility |
|---|---|---:|---|
| `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx` | Modify | +30 | Add `onFormReady?: (form) => void` and call it when the internal form instance is ready or replaced. Keep `createSchemaFormilyForm` and `applyExternalErrorsToForm` behavior unchanged. |
| `apps/web/src/features/labeling/formily/components/FieldFrame.tsx` | Modify if needed | +12 | Add a stable DOM anchor, for example `data-labeling-field-id={schemaField.stableId}`, so the page can scroll/focus the first invalid field by stableId. |
| `apps/web/src/pages/labeler/LabelerSessionPage.tsx` | Modify | +95 | Keep a Formily form ref, trigger `form.validate()` in the blocking branch, show `{field label}: {reason}`, and scroll/focus first invalid field. Do not edit `handleConfirmSubmit`. |
| `apps/web/src/features/labeling/formily/__tests__/renderer.test.tsx` | Modify | +35 | Assert `onFormReady` receives the form and existing Formily update behavior remains unchanged. |
| `apps/web/src/pages/labeler/LabelerSessionPage.submitValidationFeedback.test.tsx` | Create or modify existing test | 190 | Cover minLength blocked submit, Formily validation trigger, concrete toast, field focus/scroll target, and passing validation opening the modal. |
| Existing raw-source guard test | Modify if needed | +25 | Guard that `handleConfirmSubmit`/offline submit flow/server 422 mapping did not change. |

Estimated net LOC: about 340-390 depending on whether the page-level tests can extend an existing test file. Cap: soft 420 / hard 600, files soft 6 / hard 8. Generated churn remains 0.

## 3. Renderer Form Exposure

Preferred interface:

```ts
export interface SchemaFormilyRendererProps {
  schemaFields: SchemaField[];
  value?: AnswerPayload | null;
  onChange: (value: AnswerPayload) => void;
  readOnly?: boolean;
  errors?: Map<string, string[]>;
  onFormReady?: (form: Form<Record<string, unknown>>) => void;
}
```

`SchemaFormilyRenderer` should call `onFormReady(form)` from an effect when the internal `form` instance changes. If a cleanup is needed, it can call `onFormReady(undefined)` only if the prop type is explicitly widened; otherwise the page can replace the ref on the next mounted form. The simpler preferred path is a stable callback that writes the current form into a `useRef` in `LabelerSessionPage`.

Do not:

- lift `createSchemaFormilyForm` into `LabelerSessionPage`;
- change the dependency list that determines when the form is created, unless required by React lint and covered by tests;
- change `applyExternalErrorsToForm`, which remains for server 422/external errors.

## 4. Submit Click Flow

Current blocked path:

```ts
if (validationErrors.length > 0) {
  Toast.warning('请先修复字段错误再提交');
  return;
}
setSubmitModalOpen(true);
```

Target blocked path:

1. Read `firstError = validationErrors[0]`.
2. Trigger `formRef.current?.validate()` and swallow the expected rejection because invalid form validation commonly rejects.
3. Resolve the field label from `fields` using `firstError.stableId`.
4. Show `Toast.warning(`${label}: ${firstError.reason}`)`.
5. Scroll and focus the field matching `firstError.stableId`.
6. Return without opening the submit modal.

Passing path remains unchanged: if `validationErrors.length === 0`, open the submit confirmation modal.

## 5. Toast Rule

Toast content must use the already-computed `PayloadValidationError.reason`.

Examples:

| Error | Toast |
|---|---|
| `detailed_comment`, reason `最少 5 字` | `详细评审意见: 最少 5 字` |
| `overall_decision`, reason `此字段必填` | `总体结论: 此字段必填` |
| unknown stableId fallback | `字段: {reason}` or `{stableId}: {reason}` |

Do not invent new validation messages. Do not write "无字段限制" or similar guesses. The business rule remains defined by schema validation.

## 6. Scroll And Focus

Preferred DOM anchor:

- Add `data-labeling-field-id={schemaField.stableId}` to the `FieldFrame` root when a schema field is known.
- In `LabelerSessionPage`, use a helper such as `scrollToFieldError(stableId)`.
- Query with `CSS.escape` when available, falling back to a safe manual quote or no-scroll if the selector cannot be built.
- Call `scrollIntoView({ block: 'center', behavior: 'smooth' })`.
- Focus the first enabled control inside the field wrapper: `input`, `textarea`, `button`, `[tabindex]`, or Semi's combobox element.

If `FieldFrame` already exposes a stable selector by implementation time, reuse it instead of adding a new anchor. Do not rely on fragile label text search.

## 7. Constraints

- **Do not fight Formily**: trigger `form.validate()` and let Formily's native validators populate field errors. Do not set client errors externally through `setFieldState`.
- **Submit chain unchanged**: `handleConfirmSubmit`, `runLabelerSubmitWithOfflineDraft`, C4 submit sequence, offline draft sync, and server 422 mapping remain untouched.
- **Blocking semantics unchanged**: `validationErrors.length > 0` still blocks. C4.6 only adds visible field feedback, concrete toast, and positioning.
- **Validation logic unchanged**: do not edit `validatePayload` or `schemaToFormilyValidators`.
- **P3a/P3b guard**: linkage visibility, server validation errors, and submit 422 behavior are not weakened.
- **C4.5/P5 guard**: dataset item card and offline draft store/hydrate/sync/submit are not changed.

## 8. Tests

Required coverage:

- `SchemaFormilyRenderer` calls `onFormReady` with a Formily form instance.
- The form instance from `onFormReady` can be validated by the parent path without changing renderer creation logic.
- When `detailed_comment` has fewer than five characters, submit click:
  - does not open `SubmitConfirmModal`;
  - calls Formily validation;
  - renders/activates the field-level error `最少 5 字`;
  - shows a concrete toast containing `详细评审意见` and `最少 5 字`;
  - scrolls/focuses the `detailed_comment` field.
- When the payload is valid, submit click still opens `SubmitConfirmModal`.
- Server 422 path remains covered by existing tests and is not routed through the new submit-click feedback path.
- C4.5 dataset item context tests and P5 offline draft tests continue to pass.

Testing may use existing Vitest and React server/client test patterns. Do not add a new testing dependency.

## 9. Risks And STOP Conditions

- If route 3a requires changing Formily form creation architecture rather than exposing the existing form, STOP.
- If client feedback requires editing `validatePayload` or `schemaToFormilyValidators`, STOP.
- If field positioning cannot be implemented without fragile DOM assumptions, add a stable `FieldFrame` anchor; if that still fails, STOP.
- If implementation touches `handleConfirmSubmit`, C4 submit helpers, P5 offline draft files, C4.5 dataset item card, backend, OpenAPI, generated types, or migrations, STOP.
- If `form.validate()` cannot activate native field errors for untouched fields, STOP and report; do not switch to external `setFieldState` as a hidden route change.

## 10. Three-Viewport Note

This cluster does not add new layout, but scroll/focus behavior should be sane at 1440, 1280, and 1024 widths. The first invalid field should end up visible near the center of the viewport without horizontal scrolling. If browser verification is unavailable, report D-port and rely on unit coverage for target selection.

## 11. Caps

| Metric | Soft | Hard |
|---|---:|---:|
| Hand-written files touched | 6 | 8 |
| Hand-written net LOC | 420 | 600 |
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

Expected anchors: MD5 `1acd96fb6c0fd0e7b084245d8ae3fa76`, migrations 17, humanpending 162, generated churn 0, frontend tests increase from 200.
