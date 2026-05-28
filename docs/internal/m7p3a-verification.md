# M7-P3a Verification: Dual-Side Validation Symmetry

## 1. Status

M7-P3a closed on 2026-05-28.

Baseline: M7-P2 closed at `fe8659e`; M7-P3a scope and pre-estimate landed at
`1fbe093`. Final code head before this closure cluster: `a84c48a` (C5).
Final docs head: this commit.

Phase character: first half of M7-P3. P3a targets the runtime validation half
of rubric 1.4 by closing the backend answer-validation gap: before this phase,
`SessionService.submit` only checked `answerPayload != null`, while frontend
`payloadValidation.ts` was the only field-level validator. P3a makes submit
validation dual-sided: frontend and backend now enforce the same static field
rules, and API clients bypassing the frontend receive HTTP 422 before any
invalid answer reaches persistence.

Current anchors:

| Anchor | Value |
|---|---|
| OpenAPI MD5 | `304b6d00e35a3649fd10ae9f01392288` |
| Backend tests | `431 / 80` in the local C5 run |
| Frontend Vitest | `54` |
| Migrations | `11` |
| humanpending | `135` after this commit |

## 2. Commit Map

| Commit | Cluster | Purpose |
|---|---|---|
| `1fbe093` | docs | M7-P3a scope-budget + pre-estimate |
| `d1d6f01` | C1 | OpenAPI submit 422 response, reusing `ApiError.fieldErrors`; MD5 `b6a8344...` -> `304b6d00...` |
| `81744fc` | C2 | Backend `AnswerPayloadValidator`, `AnswerValidationException`, and GlobalExceptionHandler 422 mapping |
| `08c7c4f` | C3 | Submit-path wiring using `session.getSchemaVersionId()` for session-bound schema validation |
| `e5907aa` | C4 gate | C4 corpus scope-budget + pre-estimate |
| `8792b2d` | C4 | Shared validation corpus, backend/frontend corpus tests, and submit validation integration tests |
| `34e52bc` | C5 gate | C5 submit validation field-error surfacing scope-budget + pre-estimate |
| `a84c48a` | C5 | `SubmitValidationError`, `serverValidationErrors`, and LabelerSessionPage Path X wiring |
| (this commit) | C6 | Verification doc, humanpending entries, screenshot/D-record closure |

## 3. R8 Transparency Records

### Record A: P3a Split And Cluster Shape

```
User adjudicated M7-P3 into P3a (dual-side validation symmetry) and
P3b (field linkage DSL), with P3a first. The reason was security:
backend submit accepted any non-null answer payload, so direct API
clients could bypass frontend validation and persist invalid answers.
P3a therefore focuses only on static single-field runtime validation:
required, minLength, maxLength, min, max, pattern, option membership,
string shape for date/file, and nested recursion. Cross-field linkage
and conditional required rules are explicitly deferred to P3b.
```

### Record B: Six User Adjudication Decisions

```
1. P3a before P3b: backend validation symmetry first, linkage DSL later.
2. Validation failures return HTTP 422 Unprocessable Entity, not 400.
3. Reuse ApiError.fieldErrors; do not introduce a dedicated
   AnswerValidationProblem schema.
4. Validation-failure auditing is deferred; failed submits are rejected
   before persistence and are not state changes in this phase.
5. The shared corpus lives at packages/contracts/fixtures/
   validation-corpus.json and is read by both frontend and backend tests.
6. P3a cap: 1000 soft / 1300 hard.
```

### Record C: C2 Symmetry Traps

```
C2 mirrored frontend payloadValidation.ts, but three subtle traps were
called out and frozen:

- Empty semantics use isEmpty, not isBlank. null, "", [], and {} are
  empty; whitespace-only strings such as "   " are not empty.
- Pattern validation uses Java matcher.find(), not matcher.matches(),
  because frontend RegExp.test() is a partial-match check.
- date and file_upload remain string-shape checks only. No ISO date
  validation was added because the frontend does not enforce ISO format.

C4 corpus cases protect all three traps.
```

### Record D: C3 Session-Bound Schema Version

```
Submit validation must use the schema version bound to the session at
claim time, not the task's latest published schema. C3 wires validation
through schemaVersionMapper.selectById(session.getSchemaVersionId()).
A session claimed under schema v2 still validates against v2 even if
v3 is published later. This protects M6 schema versioning and
M6-P0.5 immutable answer facts.
```

### Record E: C4 Path C Corpus Read And Known Asymmetry

```
C4 adopted Path C for backend corpus loading: the backend test walks
upward from the current directory until it finds pnpm-workspace.yaml,
then reads packages/contracts/fixtures/validation-corpus.json. This
keeps pom.xml unchanged and avoids Maven resource-copy configuration.
The helper fails loudly with the searched paths if the repo root cannot
be found.

The corpus has 20 cases. Exactly one case is marked
expectSymmetry:false: number-min-scientific-known-asymmetry. Any second
true frontend/backend message mismatch must stop future work and be
adjudicated; it must not be hidden by another false flag.
```

### Record F: C5 Path X Field Error Surfacing

```
C5 chose Path X: serverErrors is a submit-time snapshot from backend
422 ApiError.fieldErrors. While serverErrors is present, the renderer
shows serverErrors ?? clientErrors. On any user edit,
handleAnswerPayloadChange clears serverErrors and returns the page to
frontend live validation.

The implementation avoids React Testing Library for this cluster by
testing pure helpers plus the mocked submit mutation. The full
map-to-render visual path is covered by the existing renderer error
prop infrastructure and by the C5 D-record; a future browser E2E
decision can add RTL or Playwright separately.
```

### Record G: Scientific-Notation Message Asymmetry Deferred

```
One known non-symmetric message remains deliberately exposed and
deferred. Backend formatNumber() renders 1e-7 as "0.0000001", so a
minimum error is "不能小于 0.0000001". Frontend JavaScript renders
String(0.0000001) as "1e-7", so the analogous message is
"不能小于 1e-7".

C4 records this in the shared corpus as
number-min-scientific-known-asymmetry with expectSymmetry:false. C4
does not fix it. A future adjudication must choose whether Java should
emulate JavaScript number formatting, frontend should adopt a shared
formatter, or the gap should remain a documented limitation.
```

## 4. ApiFieldError.field Dual Semantics

`ApiFieldError.field` now has two valid meanings depending on endpoint
context:

| Context | Meaning |
|---|---|
| M6 task/create/publish validation | OpenAPI camelCase request-body property name, such as `quotaTotal` or `deadlineAt` |
| M7-P3a submit validation | Dynamic `SchemaField.stableId`, such as `field_0` or a nested child stableId |

The generated schema comment still describes the M6 meaning: field names match
the OpenAPI request body schema. C1's submit 422 response description, C4's
integration test, and C5's `serverValidationErrors.ts` comment establish the
submit-context meaning: `fieldErrors[].field` is a schema stableId that maps
directly into the renderer's `errors` prop.

This reuse was intentional because Decision 3 rejected a new error schema. It
keeps the contract small, but future contract work should consider a dedicated
field-error type or stronger discriminated documentation to remove the
semantic overload. Candidate future bucket: P7 type-safety / contract
hardening.

## 5. Estimate Vs Actual

| Cluster | Estimate | Actual | Stop Triggered |
|---|---:|---:|---|
| C1 contract | 80 | 22 insertions | No |
| C2 backend validator | 250 | 241 insertions | No |
| C3 submit wiring | 40 | 49 insertions / 3 deletions | No |
| C4 corpus + tests | 750 after gate | 527 insertions / 1 deletion | No |
| C5 frontend 422 surfacing | 370 after gate | 170 insertions / 18 deletions | No |
| C6 docs closure | N/A | this commit | No |
| **Implementation total** | **~1490 post-gate sum** | **~1009 net insertions before C6** | **No hard-cap stop** |

Notes:

- Original phase cap remained 1000 soft / 1300 hard, but C4 and C5 each had
  local gates because the required test and frontend surfacing scope was more
  precise after C1-C3 evidence.
- C4's larger local estimate was accepted because it added the shared corpus,
  frontend corpus tests, backend corpus tests, and two submit integration
  proofs.
- C5 landed under its 500 soft / 700 hard cap and did not alter renderer code
  or benchmark code.

## 6. Verification And D-Records

| Check | Result |
|---|---|
| OpenAPI MD5 | `304b6d00e35a3649fd10ae9f01392288` unchanged after C1 |
| Migrations | `11` unchanged |
| humanpending | `133 -> 135` in C6, exactly two new `- [` entries |
| pom.xml | unchanged after Path C |
| Backend tests | `431 / 80` in the local C5 report |
| Frontend Vitest | `54` in the C5 report |
| Corpus | 20 cases, exactly one `expectSymmetry:false` |

D-records:

- Backend test execution in the sandbox has recurring socket and Docker
  constraints. The accepted C4/C5 report used escalated reruns where needed,
  and Testcontainers integration classes use `@Testcontainers(disabledWithoutDocker = true)`
  so Docker-backed cases skip cleanly when Docker is unavailable.
- The backend `431 / 80` count includes JUnit parameterized invocation
  counting and Docker-dependent skips. Local environments may report a small
  count variation, but failures remain the binding signal.
- C5 three-viewport screenshots were not captured because no seeded browser
  flow was available to trigger a real backend 422 in the current workspace.
  C5 instead verified the data path with mocked mutation tests, pure helper
  tests, and frozen renderer/CSS diffs. No screenshot was fabricated.
- C6 likewise records the screenshot gap rather than inventing artifacts.
  The next seeded browser pass should capture 1440 / 1280 / 1024 evidence for
  backend field errors under the form field.

## 7. Coverage Matrix

| Layer | Coverage |
|---|---|
| Contract | `POST /sessions/{sessionId}/submit` declares HTTP 422 with `ApiError` |
| Backend validator | `AnswerPayloadValidator` mirrors frontend messages and empty/type/pattern rules |
| Submit path | `SessionService.submit` rejects invalid answers before persistence |
| Versioning | Validation uses `session.getSchemaVersionId()` |
| Corpus | Shared JSON fixture drives both frontend and backend tests |
| Frontend submit | `SubmitValidationError` preserves generated `ApiFieldError[]` |
| Renderer surfacing | Labeler session maps backend `fieldErrors` to `Map<stableId, string[]>` and passes it to `SchemaFormilyRenderer` |

## 8. Watch Items

These are recorded in the verification doc only and are not new
humanpending entries.

- Autosave 422 lifecycle: `handleConfirmSubmit` disables autosave before
  submit. After a 422, the page stays on the form with server errors visible;
  the next edit calls `handleAnswerPayloadChange`, clears `serverErrors`, and
  updates `answerPayload`. Existing autosave behavior should re-enter through
  the normal answer-payload change path, but this remains worth observing in a
  seeded browser run. It is outside P3a's security boundary.
- Legacy `SchemaRenderer` remains retained from the M7-P2 watch entry as a
  fallback and benchmark baseline. P3a did not change that watch.

## 9. M6 Compatibility

| Contract | Result |
|---|---|
| M6-P0.5 immutable answer facts | Preserved. Validation happens before persistence and never mutates existing submissions. |
| Schema versioning | Preserved. Submit validation uses the session-bound schema version. |
| Trusted export reproducibility | Preserved. Invalid new submissions are rejected; persisted historical answers are untouched. |
| Audit/governance | Preserved. Validation-failure auditing was explicitly deferred and no audit pipeline code changed. |
| M7-P2 Formily runtime | Preserved. C5 uses the existing `errors` prop and leaves both renderers untouched. |

## 10. P3a Final State

M7-P3a closes the runtime validation security gap:

- Frontend validation remains in `payloadValidation.ts`.
- Backend validation now mirrors it through `AnswerPayloadValidator`.
- Submit rejects invalid payloads with HTTP 422 and `ApiError.fieldErrors`.
- `fieldErrors[].field` is a schema stableId in submit context.
- Session-bound schema versioning protects claim-time validation.
- The shared corpus is the executable symmetry proof.
- One scientific-notation message formatting mismatch is visible, documented,
  and deferred rather than hidden.

Remaining M7 direction:

- M7-P3b: field linkage DSL and cross-field runtime rules.
- Later M7 phases: contract/type-safety hardening, browser evidence cleanup,
  and any adjudicated fix for scientific-notation formatting.
