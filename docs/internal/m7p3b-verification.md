# M7-P3b Verification: Field Linkage DSL

## 1. Status

M7-P3b closed on 2026-05-28.

Baseline: M7-P3a closed with OpenAPI MD5
`304b6d00e35a3649fd10ae9f01392288`. M7-P3b research began at
`7e73e4b`. Final code head before this closure cluster: `f5110d2` (C5).
Final docs head: this commit.

Phase character: second half of M7-P3. P3a made static field validation
dual-sided. P3b adds the cross-field linkage DSL for conditional visibility
and conditional required rules while preserving the same dual-side proof
standard: frontend and backend must evaluate the same JSON DSL the same way at
both publish and runtime boundaries.

Current anchors after this closure cluster:

| Anchor | Value |
|---|---|
| OpenAPI MD5 | `890e595c6351ee53788d35354b2412a3` |
| Backend tests | `494 / 81` in the C4/C5 reports |
| Frontend Vitest | `120` |
| Migrations | `11` |
| humanpending | `140` after this commit |

## 2. Commit Map

| Commit | Cluster | Purpose |
|---|---|---|
| `7e73e4b` | research | Linkage DSL shape research and codegen shape PoC |
| `f8f94fe` | C1 gate | OpenAPI DSL contract + publish validation scope-budget/pre-estimate |
| `23f2ffa` | C1 | OpenAPI `visibleWhen` / `requiredWhen`, generated types, and dual-side publish validation |
| `da1d07e` | C2 gate | Backend runtime evaluator scope-budget/pre-estimate |
| `85d5291` | C2 | Backend `LinkageEvaluator` and `AnswerPayloadValidator` double-view coupling |
| `90fc8b6` | C3 gate | Frontend runtime evaluator + renderer filtering scope-budget/pre-estimate |
| `a16fa53` | C3 | Frontend evaluator, `payloadValidation` coupling, and renderer-external visibility filtering |
| `69221bd` | C3.5 gate | Linkage condition deserialization fix scope-budget/pre-estimate |
| `e79a51c` | C3.5 | Shape-based Jackson deserialization for stored linkage JSON |
| `29b6317` | C4 gate | Shared linkage corpus scope-budget/pre-estimate |
| `0dba6bc` | C4 | Shared linkage corpus proof, publish/runtime dual-side tests, submit integration |
| `50cb152` | C5 gate | Minimal advanced JSON editor scope-budget/pre-estimate |
| `f5110d2` | C5 | Owner designer advanced JSON entry for `visibleWhen` / `requiredWhen` |
| (this commit) | C6 | Verification doc and humanpending closure records |

## 3. DSL Final Shape

P3b v1 uses a constrained declarative JSON AST:

- one-level `allOf` / `anyOf` groups plus atomic conditions;
- no recursive condition tree in v1;
- no string expression language and no eval;
- `field` references are `SchemaField.stableId` values, including nested
  fields by the existing flat stableId convention;
- op whitelist: `eq`, `neq`, `in`, `notIn`, `gt`, `gte`, `lt`, `lte`,
  `empty`, `notEmpty`;
- `visibleWhen` controls rendering and validation participation;
- `requiredWhen` conditionally strengthens requiredness;
- hidden fields do not validate, including static required, conditional
  required, type checks, and nested children;
- hidden field values are not stripped from `AnswerPayload`;
- runtime evaluation is single-pass against the raw answer snapshot, with no
  topology sort and no iterative stabilization.

Example:

```json
{
  "visibleWhen": {
    "anyOf": [
      { "field": "type", "op": "eq", "value": "other" },
      { "field": "category", "op": "in", "value": ["custom", "manual"] }
    ]
  },
  "requiredWhen": {
    "field": "type",
    "op": "eq",
    "value": "other"
  }
}
```

## 4. R8 Transparency Records

### Record A: P3b Scope And Visual Builder Deferral

```
P3b v1 includes the architecture and runtime closure: OpenAPI DSL,
generated types, backend evaluator, frontend evaluator, publish-time
validation, runtime visibility, conditional required, shared corpus,
and submit-path symmetry. It intentionally excludes the visual
condition-tree builder, field picker, operator-specific editor,
condition previewer, and complex UX validation. C5 adds only a minimal
advanced JSON entry point in the owner schema designer. The visual
condition editor is deferred to P3b v2.
```

### Record B: One-Level DSL Instead Of Expression Language

```
User adjudication selected a constrained declarative condition object
instead of a string expression DSL. The reason was the P3a symmetry
lesson: dual-side expression evaluators would create a large semantic
surface for JS/Java drift. P3b therefore keeps the AST small enough to
prove with a shared corpus: atomic conditions plus one-level allOf /
anyOf groups and a fixed operator whitelist.
```

### Record C: Path F For LinkageConditionValue

```
C1 attempted real Java code generation and hit the predicted
openapi-generator oneOf primitive-union problem. A oneOf of primitive
values and arrays generated an unusable marker-interface shape for
Java/Jackson. User adjudication selected Path F:
LinkageConditionValue is an unconstrained OpenAPI schema, generating
TS unknown / Java Object, while publish-time validation becomes the
load-bearing enforcement for op/value shape.

This preserves the external DSL JSON shape exactly. The safety move is
not contract-level typing; it is dual-side publish validation that
requires scalar value for eq/neq/comparison, array value for in/notIn,
and absent value for empty/notEmpty.
```

### Record D: Runtime Double-View

```
C2 introduced the runtime double-view. Existing P3a validation needs a
local value for the current field, especially inside nested_object
maps. Linkage evaluation, however, reads conditions by flat stableId
from the raw answer snapshot. The validator therefore builds a flat
stableId -> value index for linkage evaluation while preserving local
nested values for the original P3a validation body.
```

### Record E: C3 Renderer-External Filtering And Minimal B

```
C3 kept SchemaFormilyRenderer unchanged. Field visibility is computed
outside the renderer by filtering the schema tree before passing it to
the renderer. The same visible schema tree is also passed to
SubmitConfirmModal ("minimal B") so the form page and confirmation
modal do not disagree about hidden fields. Validation still receives
the original schema fields; validatePayload itself skips hidden fields.
Hidden values remain in the answer payload and are not stripped.
```

### Record F: C3.5 Deserialization Blind Spot

```
C1-C3 tests mostly constructed LinkageAtomicCondition and
LinkageConditionGroup objects directly. They did not cover the real
production path where stored schema_json is a Map/JSON structure that
must be converted back into SchemaDocument. C4's round-trip probe
found the production blocker: LinkageCondition is a generated marker
interface and Jackson could not instantiate visibleWhen/requiredWhen.

C3.5 fixed this with a shape-based Jackson deserializer registered on
the Spring application ObjectMapper. field/op produces
LinkageAtomicCondition; allOf/anyOf produces LinkageConditionGroup;
field/op wins when both shapes appear, matching the publish validators
and evaluators. C3.5 also added real Spring ObjectMapper proof so the
test cannot pass with only a local mapper while production remains
broken.
```

### Record G: Convenience Constructor ObjectMapper Watch

```
SessionService has a convenience constructor that creates a new
ObjectMapper without the linkage module. Production submit uses the
Autowired main constructor and receives the Spring ObjectMapper with
the linkage module, so the production path is safe after C3.5. The
convenience constructor remains a future footgun if reused for linkage
schemas. C3.5 did not alter it because that would exceed the narrow
deserialization fix scope. This is recorded as an explicit watch item.
```

### Record H: Numeric Precision Boundary

```
C2 uses Java BigDecimal comparison while C3 uses JavaScript finite
number comparison. C4 linkage corpus includes numeric probes including
1 versus 1.0, MAX_SAFE_INTEGER, and a high-precision decimal case.
Those JSON-safe values passed symmetrically with zero
expectSymmetry:false cases. Extremely high precision values beyond JS
double semantics remain a theoretical boundary, related in spirit to
the P3a scientific-notation message asymmetry, but P3b did not observe
a runtime evaluator mismatch in the corpus.
```

## 5. Publish And Runtime Proof

P3b has two proof boundaries:

| Boundary | What is proved |
|---|---|
| Publish | Invalid DSL is rejected before schema publication on both backend and frontend |
| Runtime | Published DSL evaluates visibility and conditional required identically on backend and frontend |

C4's shared corpus is the load-bearing artifact:

| Corpus | Value |
|---|---|
| File | `packages/contracts/fixtures/linkage-corpus.json` |
| Total cases | `31` |
| Runtime cases | `21` |
| Publish cases | `10` |
| `expectSymmetry:false` | `0` |

Runtime cases assert only final validation errors, not rendered visibility
trees. This keeps the corpus focused on the persistence boundary and avoids
duplicating C3's `visibleSchemaFields` unit tests.

Publish cases assert `{ fieldPath, reason }` pairs on both sides. They do not
compare stableIds because publish errors are schema-authoring errors, not
answer field errors.

The submit integration test added in C4 verifies the full production path:
published schema JSON in persistence -> Spring `ObjectMapper` ->
`SchemaDocument` -> `AnswerPayloadValidator` -> linkage evaluator -> HTTP 422
with field errors for visible conditional-required fields while hidden fields
do not report.

## 6. Architecture Final Form

```text
Schema authoring:
  OwnerSchemaDesignerPage
    -> FieldEditor
       -> LinkageJsonEditor (syntax-only JSON entry)
    -> publishVersion
       -> frontend schemaValidation.ts
       -> backend SchemaValidator
          - op whitelist
          - op/value shape
          - missing ref
          - self-reference
          - cycle detection

Contract / persistence:
  OpenAPI SchemaField.visibleWhen / requiredWhen
    -> generated TS/Java types
    -> LinkageConditionValue as unknown/Object (Path F)
    -> schema_json storage
    -> Spring ObjectMapper linkage deserializer

Runtime answer validation:
  AnswerPayloadValidator
    -> flat stableId value index for linkage
    -> local nested value for P3a validation
    -> LinkageEvaluator
    -> hidden fields skip all validation
    -> visible requiredWhen reuses "此字段必填"

Frontend runtime:
  payloadValidation.ts
    -> same flat-index and effective-required semantics
  LabelerSessionPage
    -> visible schema tree computed outside renderer
    -> SchemaFormilyRenderer unchanged
    -> SubmitConfirmModal receives the same visible tree
```

## 7. Invariants Preserved

| Invariant | Result |
|---|---|
| P3a static validation | Preserved. Fields with no linkage behave exactly as before and P3a corpus remains green. |
| Session-bound schema version | Preserved. Submit still validates the version bound to the session at claim time. |
| Hidden value retention | Preserved. Hidden fields are not rendered or validated, but existing values are not stripped from `AnswerPayload`. |
| Renderer performance evidence | Preserved. C3 filters outside `SchemaFormilyRenderer`; renderer and benchmark internals remain unchanged. |
| No eval | Preserved. DSL is JSON AST only. |
| No visual builder | Preserved. C5 is advanced JSON only. |

## 8. Verification And D-Records

| Check | Result |
|---|---|
| OpenAPI MD5 | `890e595c6351ee53788d35354b2412a3` |
| Migrations | `11` |
| Backend tests | `494 / 81` after C4 |
| Frontend Vitest | `120` after C5 |
| humanpending | `135 -> 140` in C6 |
| Linkage corpus | `31` cases, zero asymmetric cases |

D-records:

- Browser screenshots were not captured in C5/C6 because the workspace did
  not have a seeded browser flow for owner designer linkage authoring and
  labeler runtime linkage submit. The code-level proof is strong, but visual
  evidence remains a future seeded-browser pass.
- Backend Docker/Testcontainers availability may affect local integration
  execution. The accepted C4 result records the backend count as `494 / 81`.
- Numeric runtime comparison is proven over JSON-safe corpus values, including
  high-precision decimal and safe integer probes, but not over arbitrary
  precision values outside JS number semantics.

## 9. Estimate Vs Actual Summary

P3b required one explicit interrupt cluster, C3.5, because the real
schema-json production path exposed a Jackson deserialization gap that C1-C3
unit tests did not cover. The interruption was useful: it repaired the true
production path before the shared C4 corpus and submit integration proof were
finalized.

High-level implementation spread:

| Cluster | Character |
|---|---|
| C1 | Contract + generated types + dual-side publish validation |
| C2 | Backend runtime evaluator and validator coupling |
| C3 | Frontend runtime evaluator and renderer-external filtering |
| C3.5 | Production-path deserialization fix |
| C4 | Shared corpus and integration proof |
| C5 | Minimal owner configuration entry |

C5 stayed intentionally small because the visual condition builder was
deferred. That kept P3b focused on the harder architectural proof: dual-side
publish/runtime semantics.

## 10. M7-P3b Final State

M7-P3b completes the field-linkage half of rubric 1.4:

- schema authors can persist `visibleWhen` and `requiredWhen`;
- invalid linkage DSL is rejected at publish time on both frontend and backend;
- frontend and backend runtime validators evaluate the same DSL against the
  same raw payload semantics;
- hidden fields do not validate;
- visible conditional-required fields reuse the existing required message;
- labeler rendering hides fields through an external visible schema tree while
  leaving `SchemaFormilyRenderer` unchanged;
- confirmation modal receives the same visible schema tree;
- C4's shared corpus locks runtime and publish symmetry;
- C3.5 locks the database JSON round-trip path.

Remaining M7 buckets after P3b:

- P4: prompt versioning;
- P5: draft/offline resilience;
- P6: mobile and responsive polish;
- P7: type-safety / contract hardening;
- P8: scoring calibration.
