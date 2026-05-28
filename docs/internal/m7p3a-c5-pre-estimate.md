# M7-P3a C5 Pre-Estimate: Submit 422 Field Error Surfacing

## Status

Pre-estimate gate for M7-P3a C5. No implementation code has landed for this
cluster.

Current anchor: `8792b2d`. OpenAPI MD5:
`304b6d00e35a3649fd10ae9f01392288`. Migrations: `11`. humanpending:
`133`. Frontend Vitest: `48`. Backend tests: `431/80` in the local C4 run.

## Baseline Evidence

### Existing Renderer Infrastructure

`SchemaFormilyRenderer` already accepts:

```ts
errors?: Map<string, string[]>;
```

The Formily implementation already has `applyExternalErrorsToForm()` and looks
up both path and stableId:

```ts
errors.get(path) ?? errors.get(field.stableId)
```

That exactly matches C4's backend convention that nested child validation
errors use the flat child `stableId`, not dot-path.

Legacy `SchemaRenderer` also has an `errors` prop, but C5 does not touch it.

### Current Submit Error Loss

`apps/web/src/features/labeling/useSubmitMutation.ts` currently does this on
submit failure:

```ts
throw new Error(error?.message ?? '提交失败。');
```

That loses `ApiError.fieldErrors`, so HTTP 422 validation failures cannot reach
the form.

### Current Labeler Page Behavior

`apps/web/src/pages/labeler/LabelerSessionPage.tsx` currently computes only
frontend live validation errors:

```ts
const validationErrors = useMemo(
  () => (detail && answerPayload ? validatePayload(fields, answerPayload) : []),
  [answerPayload, detail, fields],
);
const fieldErrors = useMemo(() => errorsByStableId(validationErrors), [validationErrors]);
```

It passes `fieldErrors` to `SchemaFormilyRenderer`. The submit catch only shows
a toast and never stores backend `fieldErrors`.

### Generated Type Shape

`ApiError.fieldErrors` is generated as:

```ts
fieldErrors?: components['schemas']['ApiFieldError'][];
```

`ApiFieldError` is generated as:

```ts
{
  field: string;
  message: string;
}
```

C5 must use this generated type. In submit 422 responses, `field` is a
`SchemaField.stableId`; this is the C1/C4 submit-context meaning and should be
documented at the frontend mapping boundary.

## Planned Implementation

### 1. Preserve Submit 422 Field Errors

File:

`apps/web/src/features/labeling/useSubmitMutation.ts`

Plan:

- Import generated `components` type.
- Define:

```ts
type ApiFieldError = components['schemas']['ApiFieldError'];

export class SubmitValidationError extends Error {
  constructor(
    public readonly fieldErrors: ApiFieldError[],
    message = '提交失败,请检查字段错误',
  ) {
    super(message);
  }
}
```

- Export a pure `submitSession(input: SubmitSessionInput)` function that calls
  `apiClient.POST()` and is used by `useSubmitMutation()`.
- In `submitSession()`, when `response.status === 422` and
  `error?.fieldErrors?.length`, throw `SubmitValidationError`.
- For all other failures, preserve current behavior with a plain `Error`.

This mirrors the existing typed error-class pattern in
`usePublishSchemaVersion.ts`, while avoiding a new handwritten error shape.

### 2. Add Server Error Mapping Helpers

File:

`apps/web/src/features/labeling/serverValidationErrors.ts`

Plan:

```ts
import type { components } from '../../shared/api/generated/schema';

type ApiFieldError = components['schemas']['ApiFieldError'];

export type FieldErrorMap = Map<string, string[]>;

export function fieldErrorsToStableIdMap(fieldErrors: ApiFieldError[]): FieldErrorMap {
  const map = new Map<string, string[]>();
  for (const fieldError of fieldErrors) {
    const current = map.get(fieldError.field) ?? [];
    current.push(fieldError.message);
    map.set(fieldError.field, current);
  }
  return map;
}

export function selectVisibleFieldErrors(
  clientErrors: FieldErrorMap,
  serverErrors: FieldErrorMap | null,
): FieldErrorMap {
  return serverErrors ?? clientErrors;
}
```

The comment above `fieldErrorsToStableIdMap()` records that submit 422 uses
`ApiFieldError.field` as dynamic `SchemaField.stableId`, despite the generic
OpenAPI comment still mentioning camelCase request-body properties.

### 3. Wire LabelerSessionPage

File:

`apps/web/src/pages/labeler/LabelerSessionPage.tsx`

Plan:

- Add:

```ts
const [serverErrors, setServerErrors] = useState<Map<string, string[]> | null>(null);
```

- Add a wrapped change handler:

```ts
const handleAnswerPayloadChange = useCallback((next: AnswerPayload) => {
  setServerErrors(null);
  setAnswerPayload(next);
}, []);
```

- Select visible errors:

```ts
const visibleFieldErrors = useMemo(
  () => selectVisibleFieldErrors(fieldErrors, serverErrors),
  [fieldErrors, serverErrors],
);
```

- Pass `visibleFieldErrors` to `SchemaFormilyRenderer`.
- In submit catch:

```ts
if (error instanceof SubmitValidationError) {
  setServerErrors(fieldErrorsToStableIdMap(error.fieldErrors));
  Toast.error('提交失败,请检查字段错误');
  return;
}
Toast.error('提交失败,请稍后重试');
```

No `SubmitConfirmModal` changes are expected.

## Path X Performance Notes

The server error map is created once after a submit 422. It is not recomputed
from `error.fieldErrors` during typing. On any edit, `serverErrors` becomes
`null`, so the page returns to the existing frontend `fieldErrors` memo.

The errors prop passed to the renderer is memo-selected:

```ts
serverErrors ?? fieldErrors
```

That keeps M7-P2's performance evidence intact. C5 does not touch
`SchemaFormilyRenderer.tsx`, its virtualization layer, or the benchmark file.

## Test Plan

### `useSubmitMutation.test.ts`

File:

`apps/web/src/features/labeling/useSubmitMutation.test.ts`

Cases:

1. Mock `apiClient.POST()` to return:

```ts
{
  data: undefined,
  response: { status: 422 },
  error: {
    code: 'VALIDATION_FAILED',
    message: 'Answer payload failed validation',
    fieldErrors: [{ field: 'field_0', message: '最少 5 字' }],
  },
}
```

Then `await expect(submitSession(input)).rejects.toBeInstanceOf(SubmitValidationError)`,
and assert the thrown error preserves the exact `fieldErrors`.

2. Mock a non-422 failure and assert it remains a plain `Error` with the API
message.

The test mocks the API client; it does not need React hook rendering.

### `serverValidationErrors.test.ts`

File:

`apps/web/src/features/labeling/serverValidationErrors.test.ts`

Cases:

1. `fieldErrorsToStableIdMap()` converts fieldErrors to
   `Map<stableId, string[]>`.
2. Duplicate stableIds aggregate messages in order.
3. `selectVisibleFieldErrors(client, null)` returns the client map.
4. `selectVisibleFieldErrors(client, server)` returns the server map, proving
   Path X whole-map override.
5. Edit-clear semantics are represented by passing `null` after edit; selector
   returns frontend errors again.

### Existing Guard Tests

Run but do not edit:

- Formily tests under `apps/web/src/features/labeling/formily/__tests__/`
- Benchmark file under `apps/web/src/features/labeling/__benchmarks__/`
- C4 corpus test at `apps/web/src/entities/labeling/payloadValidation.corpus.test.ts`

Expected frontend Vitest count: `48 + 7 = 55` tests, depending on exact
assertion split.

## Three-Viewport Check Plan

Preferred path:

1. Run the local app with seeded data if available.
2. Trigger a backend 422 by submitting a too-short text value.
3. Capture `1440`, `1280`, and `1024` screenshots showing the field-level
   error below the field.
4. Store artifacts under `docs/internal/` with names such as:
   - `m7p3a-c5-422-1440.png`
   - `m7p3a-c5-422-1280.png`
   - `m7p3a-c5-422-1024.png`

D-口径 fallback:

- If browser or seed data are unavailable, report the block honestly.
- Verify with jsdom/pure tests that the error map reaches the renderer prop
  boundary.
- Confirm no CSS or renderer files changed, so layout behavior is inherited
  from M7-P2's verified renderer and C5 only changes the data path.

## Estimate

| Work item | Estimate |
|---|---:|
| `useSubmitMutation.ts` typed error + `submitSession()` extraction | 55 |
| `serverValidationErrors.ts` helper | 70 |
| `LabelerSessionPage.tsx` state + catch + clear-on-edit wiring | 60 |
| `useSubmitMutation.test.ts` | 95 |
| `serverValidationErrors.test.ts` | 85 |
| `vitest.config.ts` include expansion if needed | 5 |
| Optional screenshot artifacts | N/A |
| **Total** | **370** |

Soft cap: `500`. Hard cap: `700`.

## Frozen Checks For Implementation

C5 implementation report must confirm zero diff for:

- `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx`
- `apps/web/src/features/labeling/SchemaRenderer.tsx`
- `apps/web/src/entities/labeling/payloadValidation.ts`
- `packages/contracts/fixtures/validation-corpus.json`
- `packages/contracts/openapi/labelhub.yaml`
- all backend files
- `pom.xml`
- migrations
- `humanpending.md`

Frozen anchors:

- OpenAPI MD5: `304b6d00e35a3649fd10ae9f01392288`
- Migrations: `11`
- humanpending: `133`

## Stop Conditions

- `SchemaFormilyRenderer.tsx` needs changes.
- `SchemaRenderer.tsx` needs changes.
- `payloadValidation.ts` needs changes.
- Any backend/OpenAPI/corpus file needs changes.
- The implementation needs a UI testing dependency.
- The diff exceeds `500` lines before re-estimate.
