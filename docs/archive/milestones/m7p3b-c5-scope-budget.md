# M7-P3b C5 Scope-Budget: Minimal Linkage DSL Configuration Entry

## Status

Pre-estimate gate for M7-P3b C5. No implementation code has landed for this
cluster.

Current anchor: `0dba6bc` (M7-P3b C4). OpenAPI MD5:
`890e595c6351ee53788d35354b2412a3`. Backend tests: `494/81`. Frontend
Vitest: `113`. Migrations: `11`. humanpending: `135`.

C1-C4 plus C3.5 have completed the linkage runtime pipeline:

- C1: OpenAPI `visibleWhen` / `requiredWhen`, generated types, and
  publish-time DSL validation.
- C2: backend `LinkageEvaluator` and `AnswerPayloadValidator` coupling.
- C3: frontend linkage evaluator, frontend `payloadValidation` coupling, and
  outer visible schema filtering.
- C3.5: Jackson deserialization for stored linkage conditions.
- C4: shared linkage corpus proof across backend/frontend runtime and publish
  validation.

C5 only adds the minimal owner-facing configuration ingress for the DSL.

## Phase Character

C5 is deliberately small. It does not build a visual condition editor. It
gives owners a safe, explicit "advanced JSON" entry point for `visibleWhen`
and `requiredWhen` so the already-proven runtime path can be exercised from
the designer UI.

The authoritative DSL validation remains C1 publish-time validation on both
sides. C5 only checks JSON syntax before writing a parsed condition object
into the field draft.

## Locked Scope

Included:

- Add a common linkage JSON section to the field editor UI.
- Support editing `field.visibleWhen` and `field.requiredWhen`.
- Empty input removes the corresponding property by setting it to
  `undefined`.
- Invalid JSON shows an immediate syntax error and does not call `onChange`.
- Add focused tests for parsing, write-through, invalid JSON, and empty input.
- Add minimal styles for textarea layout and error text.

Excluded:

- visual condition tree builder;
- field picker;
- operator-specific editor;
- condition preview;
- semantic DSL validation duplication;
- labeler runtime UI changes;
- renderer changes;
- fixture or seed-data work unless implementation discovers it is necessary
  for demonstration.

## Existing Touchpoints

### Field Editor

`apps/web/src/features/schema-design/field-editors/FieldEditor.tsx` is the
public dispatcher for all six field-specific editors. It currently returns the
type-specific editor directly:

```tsx
export function FieldEditor(props: FieldEditorProps) {
  switch (props.field.type) {
    case 'text':
      return <TextFieldEditor {...props} />;
    // ...
  }
}
```

This is the correct C5 hook point. C5 should wrap the selected type editor and
append one shared linkage JSON section outside the concrete editors. That keeps
the six field-type editors unchanged.

### Validation Authority

`apps/web/src/entities/schema/schemaValidation.ts` already validates:

- field references;
- self-reference;
- dependency cycles;
- group shape;
- atomic shape;
- op/value shape;
- numeric operator target type.

C5 must not duplicate these rules in the UI component. Publish remains the
authority for semantic DSL validation.

### Styling

The designer already has:

- `.field-editor`;
- `.field-editor-section`;
- `.field-editor-row`;
- `.field-error-list`;
- `.field-error-text`.

C5 should extend this style family rather than introducing a new visual system.
Textarea styling can be added under a focused class such as
`.field-linkage-json-textarea`.

## Proposed File Set

| File | Purpose | Estimate |
|---|---|---:|
| `apps/web/src/features/schema-design/field-editors/LinkageJsonEditor.tsx` | Shared advanced JSON editor for `visibleWhen` and `requiredWhen`; syntax-only parse and write-through | 135 |
| `apps/web/src/features/schema-design/field-editors/FieldEditor.tsx` | Wrap concrete editor with the shared linkage JSON editor | 25 |
| `apps/web/src/features/schema-design/field-editors/linkageJsonEditor.test.tsx` | Server-render / pure interaction tests for valid JSON, invalid JSON, and empty input | 110 |
| `apps/web/src/app/styles.css` | Minimal textarea/error styling | 30 |
| **Hand-authored total** | | **~300** |

Recommended local caps:

- soft cap: `350`;
- hard cap: `450`.

This is slightly above the user's initial `300` soft suggestion because the
component should stay isolated and tested instead of crowding `FieldEditor.tsx`
with parser state.

## Component Shape

Recommended component:

```ts
type LinkageJsonEditorProps = {
  field: SchemaField;
  onChange: (field: SchemaField) => void;
};
```

Recommended behavior:

- render two labeled textareas:
  - `visibleWhen`;
  - `requiredWhen`;
- textarea value is `''` when the property is absent;
- otherwise value is `JSON.stringify(condition, null, 2)`;
- on blur or committed change:
  - blank input -> call `onChange({ ...field, visibleWhen: undefined })`;
  - valid JSON object/array/value -> assign parsed value to the relevant
    property;
  - invalid JSON -> set local error and do not call `onChange`;
- local syntax error message: `JSON 格式错误`;
- helper text states that semantic validation happens on publish.

Implementation may choose `textarea` rather than Semi `TextArea` if the local
Semi version lacks a textarea component. The UI should still use existing
designer classes and `Typography.Text`.

## Test Strategy

C5 should not import or duplicate C1 DSL semantic validators. Tests should only
cover the UI entry behavior:

1. valid visibleWhen JSON writes a parsed condition to `field.visibleWhen`;
2. valid requiredWhen JSON writes a parsed condition to `field.requiredWhen`;
3. invalid JSON displays `JSON 格式错误` and does not call `onChange`;
4. blank input clears the property to `undefined`;
5. `FieldEditor` renders the linkage JSON section for at least one field type,
   proving the common wrapper is active.

If component interaction tests become awkward without React Testing Library,
implementation may extract pure helpers:

- `formatLinkageConditionForEditor(condition)`;
- `parseLinkageConditionInput(text)`;
- `applyLinkageConditionPatch(field, key, parsed)`.

The tests can then cover those helpers plus a server-render smoke test for the
section. Do not introduce React Testing Library in C5.

## Three-Viewport Plan

C5 changes owner designer UI. Implementation should try to inspect the owner
designer page at `1440`, `1280`, and `1024` widths with a schema field
selected. If local seed/auth/browser access blocks that, report D-record
instead:

- no runtime renderer or labeler CSS changed;
- new UI lives inside `.field-editor` / `.field-editor-section`;
- tests verify the section renders and parser behavior works.

Do not fake screenshots.

## Frozen Boundaries

C5 must not edit:

- OpenAPI or generated API types;
- backend files;
- `packages/contracts/fixtures/linkage-corpus.json`;
- `packages/contracts/fixtures/validation-corpus.json`;
- `apps/web/src/entities/labeling/linkageEvaluator.ts`;
- `apps/web/src/entities/labeling/payloadValidation.ts`;
- `apps/web/src/entities/schema/schemaValidation.ts`;
- `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx`;
- `apps/web/src/features/labeling/SchemaRenderer.tsx`;
- labeler pages;
- migrations;
- `pom.xml`;
- `humanpending.md`.

## Verification Plan

Implementation should report:

- OpenAPI MD5 remains `890e595c6351ee53788d35354b2412a3`;
- migrations remain `11`;
- humanpending remains `135`;
- frozen file diff checks are empty;
- `pnpm --filter @labelhub/web typecheck` passes;
- `pnpm --filter @labelhub/web build` passes;
- `pnpm --filter @labelhub/web test` passes and frontend test count rises
  from `113`;
- C1-C4 tests remain green;
- three-viewport screenshots or D-record.

## Stop Conditions

Stop before implementation if:

- the UI requires a semantic DSL validator copy;
- the shared editor cannot be placed outside the concrete field editors;
- the change requires labeler runtime or renderer edits;
- implementation approaches the `450` hard cap;
- tests require adding React Testing Library or another dependency.

