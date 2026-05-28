# M7-P3b C5 Pre-Estimate: Advanced JSON Linkage Entry

## Status

Pre-estimate gate for M7-P3b C5. No implementation code has landed for this
cluster.

Current anchor: `0dba6bc`. OpenAPI MD5:
`890e595c6351ee53788d35354b2412a3`. Backend tests: `494/81`. Frontend
Vitest: `113`. Migrations: `11`. humanpending: `135`.

C5 supplies the minimal owner-facing configuration path for the linkage DSL.
The runtime, publish validation, deserialization, and shared corpus proof are
already complete.

## Recommendation

Build a shared `LinkageJsonEditor` component and render it from the common
`FieldEditor` dispatcher after the concrete type editor.

This keeps the implementation small and avoids editing six field-specific
editors. It also preserves the P3b v1 scope decision: schema JSON can accept
`visibleWhen` / `requiredWhen`, but visual condition building is deferred.

## File Plan

### `LinkageJsonEditor.tsx`

Create:

```text
apps/web/src/features/schema-design/field-editors/LinkageJsonEditor.tsx
```

Responsibilities:

- display an "高级联动 JSON" editor section;
- provide one textarea for `visibleWhen`;
- provide one textarea for `requiredWhen`;
- show `JSON 格式错误` for syntax errors;
- only call `onChange` when JSON parses or when input is blank;
- blank input clears the property to `undefined`;
- include small helper copy that publish validates DSL semantics.

Estimated size: `135` lines.

Implementation detail:

```ts
type LinkageKey = 'visibleWhen' | 'requiredWhen';

function formatCondition(condition: SchemaField[LinkageKey]): string {
  return condition ? JSON.stringify(condition, null, 2) : '';
}

function parseConditionInput(value: string): { ok: true; value: unknown | undefined } | { ok: false; reason: string } {
  if (value.trim() === '') return { ok: true, value: undefined };
  try {
    return { ok: true, value: JSON.parse(value) };
  } catch {
    return { ok: false, reason: 'JSON 格式错误' };
  }
}
```

The parsed value is assigned as the generated type's linkage condition. C5
does not validate its semantic shape because C1 publish validation is the
authority.

### `FieldEditor.tsx`

Modify:

```text
apps/web/src/features/schema-design/field-editors/FieldEditor.tsx
```

Recommended shape:

```tsx
export function FieldEditor(props: FieldEditorProps) {
  const editor = renderConcreteFieldEditor(props);

  return (
    <div className="field-editor-stack">
      {editor}
      <LinkageJsonEditor field={props.field} onChange={props.onChange} />
    </div>
  );
}
```

If `.field-editor-stack` is unnecessary because each concrete editor already
returns `.field-editor`, implementation may return a fragment instead. The
goal is to add the shared section once, not duplicate it inside each editor.

Estimated size: `25` changed lines.

### Tests

Create:

```text
apps/web/src/features/schema-design/field-editors/linkageJsonEditor.test.tsx
```

Tests:

1. `parseConditionInput` accepts valid JSON and returns the parsed object.
2. `parseConditionInput` rejects invalid JSON with `JSON 格式错误`.
3. blank input maps to `undefined`.
4. applying visibleWhen writes `{ ...field, visibleWhen: parsed }`.
5. applying requiredWhen writes `{ ...field, requiredWhen: parsed }`.
6. server-rendering `FieldEditor` for a text field includes the advanced JSON
   section label.

Estimated size: `110` lines.

No React Testing Library is needed. Server rendering via
`react-dom/server` is enough for the section smoke test; pure helpers cover
the write-through behavior.

### Styles

Modify:

```text
apps/web/src/app/styles.css
```

Add narrowly scoped styles:

```css
.field-linkage-json-textarea { ... }
.field-linkage-json-error { ... }
.field-linkage-json-help { ... }
```

Estimated size: `30` lines.

### Optional Dev Fixture

Do not add a dev fixture in C5.

Reason: C4 already has a comprehensive executable corpus, and C5 only exposes
manual JSON entry. A fixture would create extra surface without improving the
minimal owner-entry story. If demonstration data is needed, C6 can document a
small JSON snippet in verification docs instead of adding code.

## Syntax-Only Validation Boundary

C5 must validate only JSON syntax.

It must not reimplement:

- op whitelist;
- field reference checks;
- self-reference checks;
- cycle detection;
- op/value shape checks;
- numeric operator target type checks.

Those rules already live in:

- backend `SchemaValidator`;
- frontend `schemaValidation.ts`;
- shared C4 linkage corpus.

This boundary matters because a second UI-specific semantic validator would
create drift risk and weaken the C1/C4 authority trail.

## Error And Write Semantics

Recommended behavior for one textarea:

| Input | Local state | `onChange` |
|---|---|---|
| valid JSON object | clear local syntax error | write parsed object to the selected property |
| valid JSON scalar/array | clear local syntax error | write parsed JSON value; publish validation later rejects semantic shape if invalid |
| invalid JSON | set `JSON 格式错误` | do not call |
| blank / whitespace-only | clear local syntax error | write `undefined` to the selected property |

Allowing any syntactically valid JSON is intentional. C5 is not the semantic
gate; publish is.

## Expected User Flow

1. Owner selects a field in the schema designer.
2. Owner expands or sees the `高级联动 JSON` section.
3. Owner pastes:

```json
{
  "field": "type",
  "op": "eq",
  "value": "other"
}
```

into `requiredWhen`.
4. The draft field now carries `requiredWhen`.
5. Publish runs existing C1 DSL validation.
6. Runtime C2/C3 linkage semantics already apply.

## Estimate

| File | Estimate |
|---|---:|
| `LinkageJsonEditor.tsx` | 135 |
| `FieldEditor.tsx` | 25 |
| `linkageJsonEditor.test.tsx` | 110 |
| `styles.css` | 30 |
| **Total** | **300** |

Recommended caps:

- soft cap: `350`;
- hard cap: `450`.

The soft cap is set above the nominal 300 estimate to allow small CSS and test
adjustments while keeping C5 firmly smaller than C1-C4.

## Test Plan

Run:

```bash
pnpm --filter @labelhub/web test
pnpm --filter @labelhub/web typecheck
pnpm --filter @labelhub/web build
```

Expected:

- frontend test count rises from `113`;
- existing C1-C4 tests stay green;
- no backend tests are necessary unless implementation accidentally touches
  backend files, which it must not.

## Three-Viewport Plan

Preferred:

- run the web app;
- open owner schema designer with a selected field;
- inspect `1440`, `1280`, and `1024` widths;
- verify the advanced JSON section does not overflow and remains usable.

D-record if browser/seed data is unavailable:

- document that only owner designer CSS changed;
- renderer/labeler UI untouched;
- server-render smoke test proves section presence;
- parser/write helper tests prove data behavior.

## Frozen Checks

Implementation report must confirm empty diff for:

- `packages/contracts/openapi/labelhub.yaml`;
- backend source and tests except none expected;
- `packages/contracts/fixtures/linkage-corpus.json`;
- `packages/contracts/fixtures/validation-corpus.json`;
- `apps/web/src/entities/labeling/linkageEvaluator.ts`;
- `apps/web/src/entities/labeling/payloadValidation.ts`;
- `apps/web/src/entities/schema/schemaValidation.ts`;
- `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx`;
- labeler pages;
- generated API types;
- `pom.xml`;
- migrations;
- `humanpending.md`.

## Risks

| Risk | Mitigation |
|---|---|
| C5 grows into a visual builder | Keep two raw JSON textareas; C6 records visual builder defer watch. |
| UI semantic validation drifts from C1 | Validate syntax only; publish remains authoritative. |
| Invalid JSON mutates field state | Local syntax error blocks `onChange`. |
| Blank input leaves stale condition | Blank explicitly writes `undefined`. |
| Styling disrupts the designer | Append inside existing `.field-editor-section` visual language and check three widths. |

## Adjudication Items

1. Approve common `LinkageJsonEditor` rendered from `FieldEditor.tsx`.
2. Approve syntax-only JSON validation; semantic DSL validation remains C1
   publish-time authority.
3. Approve no dev fixture in C5.
4. Approve local caps: soft `350`, hard `450`.
5. Approve tests via pure helpers + server-render smoke, with no React Testing
   Library dependency.

## Stop Conditions For Implementation

- implementation needs renderer or labeler changes;
- implementation needs backend or OpenAPI changes;
- semantic DSL validation appears necessary in the editor;
- adding React Testing Library or any dependency becomes necessary;
- line count approaches `450`.

