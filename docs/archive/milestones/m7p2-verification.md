# M7-P2 Verification: Path A-II Formily Runtime Adoption

## 1. Status

M7-P2 closed on 2026-05-27.

Baseline: `4cafa2e` through active Path A-II planning at `f37d2f9`.
The Path A-II plan supersedes the initial Path B+C plan at `76d61b2`.
Final code head: `8ec5dc6` (C7). Final docs head: this commit.

Phase character: second sub-phase of M7. Targets rubric sub-criteria 1.4
(Formily + 拖拽库, 2 -> 5 literal satisfaction) and 4.2
(大表单渲染性能, 2 -> 5 with benchmark evidence).

## 2. Commit Map

| Commit | Cluster | Purpose |
|---|---|---|
| `76d61b2` | docs (superseded) | Initial Path B+C scope-budget + pre-estimate (auditor recommendation) |
| `c669cbd` | research | Formily adoption feasibility research |
| `f37d2f9` | docs (active) | Path A-II scope-budget + pre-estimate (user adjudication) |
| `66c2e33` | C1 | Throwaway PoC results appendix |
| `56ce4e3` | C2 | Core adapters (SchemaField <-> ISchema, AnswerPayload <-> Formily) |
| `68c5fa8` | C3 | Formily renderer + 6 Semi x-components |
| `1f5ed15` | C4 | Validation projection layer |
| `54d3fd2` | C5 | Designer one-way preview panel |
| `70b7708` | C6 | Virtualization + Vitest benchmark |
| `8ec5dc6` | C7 | Consumer page swap + regression |
| (this commit) | C8 | Verification doc + screenshots + humanpending |

## 3. R8 Transparency Records

### Record A: Path B+C Auditor Recommendation Superseded By User Adjudication Of Path A

```
M7-P2 began with auditor recommendation Path B+C (custom renderer
extension + remove Formily phantom dependency). User adjudication
selected Path A (adopt Formily) on the basis that rubric 1.4
"推荐栈: Formily + 拖拽库" is a project hard requirement, not a
guideline. Auditor push-back twice on Path A's cost (~2.5x scope,
3x risk vs Path B). User re-confirmed Path A regardless of cost.
Path B+C scope-budget + pre-estimate at 76d61b2 preserved as the
auditor's superseded recommendation; Path A-II plan at f37d2f9
became the active plan.

This trail is preserved so future readers see both decision
branches without retroactive edits. The user's adjudication
authority is explicit: when user interprets a rubric as hard
requirement, the architect auditor's role is to push back once
with cost analysis and then execute the user's choice.
```

### Record B: 5 User Adjudication Decisions Verbatim

```
1. Path A locked, rubric 1.4 截图 cited as hard requirement
2. Option II (runtime Formily + retain dnd-kit designer); Designable
   + AntD path REJECTED (React 16/17 + AntD peer incompatible)
3. Local Semi x-components; @formily/semi REJECTED (peer-version
   conflict with @formily/core@2.3.7)
4. C1 PoC throwaway (baseline numbers in doc, no code commit)
5. @tanstack/react-virtual ACCEPTED; Vitest bench ACCEPTED
6. Cap: 3300 soft / 4000 hard
7. (Re-adjudicated after C1) Vitest tooling enters in C6
```

### Record C: C1 PoC Self-Correction (Vitest Phantom Assumption)

```
C1 PoC validated V1-V4 (Vite+TS+React 18+Formily compat;
AnswerPayload round-trip; stableId dot-path; react-virtual + Formily
co-existence). V5 baseline benchmark discovered that Vitest was NOT
already installed despite Decision 4's earlier assumption. C1
captured a Node react-dom/server proxy baseline and explicitly
recorded the need for C6 to install Vitest (or re-adjudicate
benchmark tooling). User re-adjudicated to install Vitest in C6.

This is an R8 self-correction: an assumption shared between auditor
and agent (Vitest already present) was falsified by direct evidence
in C1. The phase plan adapted rather than papering over the
discovery.
```

### Record D: Agent Self-Correction On Field Type Count (C2)

```
C2 Core Adapters revealed that the project has 7 SchemaFieldTypes
(text, number, single_select, multi_select, date, file_upload,
nested_object), not 6 as the auditor's C2 prompt assumed. Agent
implemented all 7 against the actual schema enum, with
single_select and multi_select sharing LabelHubSelectField. The
componentRegistry uses `satisfies Record<LabelHubSchemaFieldType,
string>` so any future SchemaFieldType addition causes a TypeScript
error in the registry — a machine-executable completeness
invariant.

This is the first time in the M7 phase trail where the agent
corrected the auditor's prompt-level assumption against actual
project evidence, not the other way around.
```

### Record E: Schema-Versioning Asymmetry Design (C2)

```
The adapter layer enforces asymmetric schema-versioning handling:

- Outbound (AnswerPayload → Formily initialValues): preserve ALL
  keys from the payload, even keys not in current SchemaField[].
  Reason: M6-P0.5 immutability — historical submissions reference
  fields removed in newer schema versions, but the immutable answer
  fact must round-trip without data loss.

- Inbound (Formily form.values → AnswerPayload): filter to keys
  present in the current SchemaField[]; drop Formily-internal keys
  ($..., _void_...). Reason: only schema-validated fields enter the
  persistence path; trusted-export reproducibility depends on saving
  the current schema's answer fact.

This asymmetry is the load-bearing design that allows Formily
runtime to coexist with M6 contracts. Recorded verbatim in
answerPayloadToFormilyValues.ts and formilyValuesToAnswerPayload.ts
file headers.
```

### Record F: REQUIRES_NEW-style Authority Asymmetry (C4)

```
Validation has two layers:

- payloadValidation.ts (apps/web/src/entities/labeling/) is the
  SUBMIT-TIME AUTHORITY for all submissions
- Formily x-validator is a LIVE UI FEEDBACK projection of the
  subset of rules expressible natively in Formily

The contract is asymmetric:
- Formily UI rules MAY be less strict (e.g., no cross-field
  constraints)
- Formily UI rules MUST NOT be more strict than payloadValidation
  (a value passing Formily must also pass payloadValidation)

The invariant is tested by an asymmetry round-trip test in
runValidationTests.ts and the validation/AUTHORITY.md policy file
documents this verbatim. Future maintainers reading the validators
adapter or the policy doc will see the boundary explicitly.
```

### Record G: Benchmark Trade-off Honest Disclosure

```
Vitest bench results at C6 (machine: MacBook Pro M4 Pro, Node v26,
darwin arm64, jsdom 29):

- Legacy SchemaRenderer first-render 1000 fields: 8.44 ms
- Formily renderer first-render 1000 fields: 25.47 ms
- Legacy single-field-change @ 500 fields: 500 renderer invocations
- Formily single-field-change @ 500 fields: 1 renderer invocation

Trade-off honest disclosure: Formily first-render is ~3x slower
than legacy due to the fixed cost of reactive form setup +
per-field subscription creation + ISchema mapper output. Formily
single-field-change is ~500x faster (1 invocation vs 500) because
Formily's reactive isolation means a keystroke does not re-render
sibling fields.

This is a trade: pay a few extra milliseconds at first paint to
buy 500x lower re-render churn on every subsequent keystroke. For
forms with > 50 fields and any user interaction, the trade is
strongly positive. For forms with < 50 fields and no interaction,
the trade is neutral or slightly negative.

The rubric 4.2 evidence is the "1 invocation" number — this is
what users feel as input fluidity. The "3x slower first paint"
fact is recorded here as the honest cost, not hidden.

Note: SchemaRenderer first-render benchmarks use react-dom/server
SSR rendering of synthetic field stubs. Real-browser first paint
includes hydration, layout, and CSS — those costs are common to
both renderers and not captured by this isolated benchmark.
```

## 4. Coverage Matrix Actual Vs Planned

| LabelHub field type | Formily component | Status |
|---|---|---|
| `text` | `LabelHubTextField` | Implemented |
| `number` | `LabelHubNumberField` | Implemented |
| `single_select` | `LabelHubSelectField` (single mode) | Implemented |
| `multi_select` | `LabelHubSelectField` (multiple mode) | Implemented |
| `date` | `LabelHubDateField` (ISO 8601) | Implemented |
| `file_upload` | `LabelHubFileUploadField` (M2 placeholder, awaits M3 S3) | Implemented |
| `nested_object` | `LabelHubNestedObjectField` (recursive) | Implemented |

Consumer surfaces on `SchemaFormilyRenderer`:

| Surface | Status |
|---|---|
| `LabelerSessionPage` | Swapped in C7 |
| `LabelerSubmissionPage` | Swapped in C7 |
| `OwnerSubmissionPage` | Swapped in C7 |
| `ReviewerSubmissionPage` | Swapped in C7 |
| `OwnerSchemaDesignerPage` | Preview panel added in C5; designer itself unchanged |

Legacy `SchemaRenderer` + 6 field-renderers are retained as fallback per Path X.

## 5. Cluster Estimate Vs Actual

| Cluster | Estimate | Actual | Stop Triggered |
|---|---:|---:|---|
| C1 (throwaway PoC) | 180 (not counted) | 165 docs | No |
| C2 (core adapters) | 500 | 424 | No |
| C3 (renderer + 6 components) | 650 | 501 | No |
| C4 (validation) | 350 | 168 | No |
| C5 (designer preview) | 300 | 201 | No |
| C6 (virtualization + benchmark) | 450 | ~745 net (1212 incl. inline test deletions) | No |
| C7 (consumer swap + regression) | 250 | 137 | No |
| C8 (docs closure) | N/A | this commit | No |
| **Cap-tracked total** | **2500** | **~2176** | **None** |

Notes:

- C1 spike was throwaway as planned; baseline numbers are in the pre-estimate appendix.
- C6 net code includes Vitest migration of inline tests, benchmark suite, and virtualization.
- Phase landed under the 2500 soft estimate and well under the 3300 hard cap.
- This differs from M7-P1's estimate overrun because C1 pre-validated the technical path before implementation.

## 6. Path A-II Architecture Final Form

```text
Runtime renderer:
  SchemaFormilyRenderer
    -> Formily form with adapters
    -> 6 local Semi x-components
    -> Threshold-based virtualization via @tanstack/react-virtual at >50 fields

Designer:
  OwnerSchemaDesignerPage (unchanged dnd-kit logic)
    -> SchemaFormilyPreviewPanel (one-way READ, runs SchemaFormilyRenderer)

Validation:
  Formily x-validator (UI feedback, subset)
    + payloadValidation.ts (SUBMIT-TIME AUTHORITY, full ruleset)

Persistence boundary:
  AnswerPayload <-> Formily values via two adapters with documented
  schema-versioning asymmetry

Legacy:
  SchemaRenderer.tsx + 6 field-renderers retained as fallback +
  benchmark baseline; humanpending [M7-P2 watch] tracks future
  cleanup phase
```

## 7. M6 Compatibility Audit Results

| M6 contract | Result |
|---|---|
| M6-P0.5 submission immutability | Preserved via outbound adapter preserving all keys including historical ones |
| M6-P1 form rendering basics | Preserved via x-component visual parity and 27 Vitest tests |
| M6-P3a/P3a-2 AI findings stableId | Preserved; AI findings render in `AiReviewDrawer`, decoupled from form renderer |
| M6-P5 trusted export reproducibility | Preserved via round-trip integrity tests for all field types |
| M6-P6c reviewer read-only | Preserved via readPretty mode + integration test |
| M6-P7 / M7-P1 audit governance | Preserved; frontend rendering does not touch audit pipeline |

No M6 contract was modified or weakened.

## 8. New Dependencies

| Package | Version | Reason | Size impact |
|---|---|---|---|
| `@tanstack/react-virtual` | `^3` | Top-level field list virtualization at >50 fields | ~5 KB gzipped |
| `vitest` | `^2` | Frontend test + benchmark runner | dev-only |
| `jsdom` | `^29.1.1` | Vitest test environment | dev-only |

Lockfile churn was expected and one-time.

## 9. Vitest Test Suite Result

27 Vitest tests pass at final code head:

- `adapters.test.ts`: 13 tests (C2 + C4 migrated)
- `renderer.test.tsx`: 5 tests (C3 migrated)
- `preview.test.tsx`: 3 tests (C5 migrated)
- `integration.test.tsx`: 6 tests (C7 new)

Coverage themes: adapters, schema-versioning asymmetry, validation authority,
renderer emit path, preview one-way state, consumer import guard, read-only mode,
external error injection, and AI stableId decoupling.

## 10. OpenAPI / Migrations / humanpending

| Anchor | Pre-P2 | Post-P2 |
|---|---|---|
| OpenAPI MD5 | `b6a8344f2c7cc38db958eb333334ebd1` | `b6a8344f2c7cc38db958eb333334ebd1` (unchanged) |
| Migrations | 11 | 11 (unchanged) |
| humanpending | 131 | 133 (+2: M7-P2 watch in C7, M7-P2 resolved in C8) |
| Backend tests | 408/78 | 408/78 (unchanged) |
| Frontend tests | 0 (no Vitest) | 27 |
| README benchmark table | N/A | Added in C6 |

## 11. Visual Verification Gap

3-viewport manual browser sanity at 1440 / 1280 / 1024 was attempted during
C5-C7 but blocked by environment-data state: local workspace lacked seeded
schema, task, submission, owner-review, and reviewer fixtures for the target
pages.

Resolution in C8:

- Six PNG evidence cards are archived under `docs/screenshots/m7p2-after-set/`.
- `06-formily-benchmark-output.png` records the actual C6 benchmark numbers.
- `01` through `05` are explicitly labeled evidence cards, not simulated UI
  screenshots. They record the consumer surface, implementation status, and
  seeded browser data gap honestly.

Remaining watch item: the C7 `[M7-P2 watch]` humanpending entry tracks future
seeded browser regression evidence across labeler, owner, reviewer, and
designer surfaces before removing the legacy renderer fallback.

## 12. M7-P2 Final State

- Rubric 1.4 (Formily + 拖拽库): literal satisfaction through Formily runtime
  renderer plus retained dnd-kit designer.
- Rubric 4.2 (大表单渲染性能): 1 renderer invocation for a 500-field
  single-field change vs legacy 500 invocations; 1000-field first render under
  200 ms in Vitest bench.
- Runtime form engine: `SchemaFormilyRenderer` with 6 local Semi x-components
  covering 7 SchemaFieldTypes.
- Virtualization: `@tanstack/react-virtual` threshold at >50 top-level fields;
  nested objects render inline.
- Tests: 27 Vitest tests.
- Legacy fallback: retained intentionally with C7 watch entry.
- Backend / OpenAPI / migrations / audit pipeline unchanged.

M7-P3 (字段联动 DSL + 双端校验) follows with a new pre-estimate gate.
