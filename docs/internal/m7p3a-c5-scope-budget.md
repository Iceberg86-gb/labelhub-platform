# M7-P3a C5 Scope-Budget: Backend Field Errors To Form Renderer

## Status

C5 pre-estimate gate. No frontend implementation code lands until this gate is
approved.

Current anchor: `8792b2d` (C4 shared corpus and tests). OpenAPI MD5:
`304b6d00e35a3649fd10ae9f01392288`. Migrations: `11`. humanpending:
`133`. Frontend Vitest: `48`. Backend tests: `431/80` in the local C4 run
after sandbox escalation.

## Phase Character

M7-P3a C5 is the frontend surfacing cluster for backend submit validation.
C1-C4 made answer-payload validation authoritative on the backend and proved
frontend/backend symmetry. C5 closes the user-facing loop: when submit returns
HTTP 422 with `ApiError.fieldErrors`, those stableId-keyed messages must appear
under the corresponding fields in `SchemaFormilyRenderer`.

This is wiring work, not renderer work. M7-P2 already built the renderer error
injection path:

- `SchemaFormilyRenderer` accepts `errors?: Map<string, string[]>`.
- `applyExternalErrorsToForm()` pushes external errors into Formily field
  `selfErrors`.
- The lookup already supports `errors.get(path) ?? errors.get(stableId)`,
  which matches C2/C4's flat child stableId backend convention.
- Legacy `SchemaRenderer` also has an `errors` prop and remains unchanged.

## Locked Goal

1. **Preserve backend field errors**: `useSubmitMutation` must not collapse
   422 `ApiError.fieldErrors` into a plain `Error.message`.

2. **Display 422 field errors on the form**: `LabelerSessionPage` converts
   backend `fieldErrors[]` to `Map<stableId, string[]>` and passes that map to
   `SchemaFormilyRenderer.errors`.

3. **Path X snapshot semantics**: backend server errors are a submit snapshot.
   Any user edit clears the server-errors state and returns the page to
   frontend live validation.

4. **Preserve M7-P2 performance evidence**: the server-errors map is stable
   between submit and the next edit. It is not recomputed on every keystroke,
   and C5 does not touch the renderer or benchmark path.

## Adjudicated Path X

Path X is locked:

- On HTTP 422, store backend `fieldErrors` as `serverErrors`.
- On any `onChange` from the form, clear `serverErrors` and update
  `answerPayload`.
- When `serverErrors` is non-null, display that map. When it is null, display
  frontend `fieldErrors`.
- The simple implementation is whole-map override, not per-key merge. Backend
  errors are submit-time authority for that failed submit snapshot; frontend
  validation resumes after the next edit.

## Allowed Files And Budget

| File | Purpose | Estimate |
|---|---|---:|
| `apps/web/src/features/labeling/useSubmitMutation.ts` | Add typed `SubmitValidationError` and preserve generated `ApiFieldError[]` on submit 422 | 55 |
| `apps/web/src/features/labeling/serverValidationErrors.ts` | New pure helpers: `fieldErrorsToStableIdMap()` and `selectVisibleFieldErrors()` | 70 |
| `apps/web/src/pages/labeler/LabelerSessionPage.tsx` | Add `serverErrors` state, clear-on-edit handler, and errors-map selection | 60 |
| `apps/web/src/features/labeling/useSubmitMutation.test.ts` | Mock `apiClient.POST`; assert 422 throws `SubmitValidationError` with fieldErrors preserved | 95 |
| `apps/web/src/features/labeling/serverValidationErrors.test.ts` | Pure helper tests: aggregate duplicate stableIds, Path X override, edit-clear semantics | 85 |
| `apps/web/vitest.config.ts` | Include the new feature-level tests if current include globs do not already cover them | 5 |
| Optional screenshot artifacts under `docs/internal/m7p3a-c5-*` | 3-viewport evidence if browser/data are available | N/A |
| **Total** | | **370** |

Soft cap: `500`. Hard cap: `700`.

The implementation has about 130 lines of slack before the soft cap. If tests
need a small local fixture helper, it should stay within that slack. If the
implementation approaches 500 lines, stop and re-estimate before coding more.

## Forbidden Surfaces

- `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx`
- `apps/web/src/features/labeling/SchemaRenderer.tsx`
- `apps/web/src/entities/labeling/payloadValidation.ts`
- `apps/web/src/features/labeling/formily/__tests__/`
- `apps/web/src/features/labeling/__benchmarks__/SchemaRenderer.bench.tsx`
- `apps/web/src/entities/labeling/payloadValidation.corpus.test.ts`
- Any backend file
- `packages/contracts/openapi/labelhub.yaml`
- `packages/contracts/fixtures/validation-corpus.json`
- `pom.xml`, migrations, and `humanpending.md`

C5 uses the renderer infrastructure; it does not revise it.

## Generated Type Requirement

`SubmitValidationError` must use the generated type:

```ts
import type { components } from '../../shared/api/generated/schema';

type ApiFieldError = components['schemas']['ApiFieldError'];
```

No new handwritten `ApiFieldError` interface is allowed.

The OpenAPI generated comment still says `ApiFieldError.field` is an OpenAPI
camelCase request property. In the submit-validation 422 context, C1's response
description and C4's integration test establish that `field` means dynamic
`SchemaField.stableId`. C5 should include a short code comment at the mapping
boundary, not change the contract.

## Guard Tests That Must Remain Untouched

These existing files are guardrails, not C5 implementation targets:

- `apps/web/src/features/labeling/formily/__tests__/adapters.test.ts`
- `apps/web/src/features/labeling/formily/__tests__/renderer.test.tsx`
- `apps/web/src/features/labeling/formily/__tests__/preview.test.tsx`
- `apps/web/src/features/labeling/formily/__tests__/integration.test.tsx`
- `apps/web/src/features/labeling/__benchmarks__/SchemaRenderer.bench.tsx`
- `apps/web/src/entities/labeling/payloadValidation.corpus.test.ts`

C5 may run them; it must not edit or weaken them.

## Risk Register

| Risk | Resolution |
|---|---|
| 422 `fieldErrors` are still lost inside `useSubmitMutation` | Add typed `SubmitValidationError` carrying generated `ApiFieldError[]`; test the mocked 422 path. |
| Backend errors linger after the user edits a field | Wrap renderer `onChange` in `handleAnswerPayloadChange()` that clears `serverErrors` before setting payload; pure helper test records the intended state transition. |
| Server errors recompute on every keystroke and regress M7-P2 evidence | Store server errors in state once. Use `useMemo` to choose `serverErrors ?? fieldErrors`; clear server errors on edit instead of merging every render. |
| Generated `ApiFieldError.field` comment confuses stableId mapping | Mapping helper comment states submit 422 uses `field = SchemaField.stableId`, matching C1/C4. |
| Tests become brittle by rendering the whole labeler page | Prefer pure helper tests and a small mocked mutation test; do not introduce a new UI testing library. |
| Need to touch renderer internals to show errors | Stop and report; renderer error path is already present and locked from M7-P2. |
| Browser/data seeding blocks screenshots | Report D-口径 honestly; fall back to jsdom/pure tests plus no-CSS-change evidence. |

## Stop Conditions

- The implementation needs changes to either renderer.
- The implementation needs backend/OpenAPI/corpus changes.
- `payloadValidation.ts` must change to make frontend errors display.
- The C5 diff exceeds 500 lines without re-estimate.
- Any formily guard test or benchmark file needs editing.
- Server error handling requires a new dependency or broad testing framework.

## Verification Plan For C5 Implementation

- `pnpm --filter @labelhub/web typecheck`
- `pnpm --filter @labelhub/web build`
- `pnpm --filter @labelhub/web test`
- Run or preserve the benchmark evidence:
  - either `pnpm --filter @labelhub/web bench`, or
  - document why no benchmark rerun is needed and verify C5 did not touch
    `SchemaFormilyRenderer.tsx` or the benchmark file.
- `bash scripts/check-protected-endpoints.sh`
- OpenAPI MD5 remains `304b6d00e35a3649fd10ae9f01392288`.
- Migration count remains `11`.
- humanpending count remains `133`.
- Frozen diff checks for both renderers, `payloadValidation.ts`, backend files,
  `pom.xml`, and `validation-corpus.json`.
- Three-viewport manual check at `1440`, `1280`, and `1024` if browser/data are
  available; otherwise D-口径 with jsdom/no-CSS-change evidence.

## User Adjudication Checklist Before Implementation

1. Approve Path X as whole-map server-error override until next edit.
2. Approve the helper-file split (`serverValidationErrors.ts`) to keep
   `LabelerSessionPage.tsx` small.
3. Approve the 370-line estimate under soft cap 500 / hard cap 700.
4. Approve pure-helper and mocked-mutation tests instead of a full page render
   test, because no React Testing Library dependency exists.
