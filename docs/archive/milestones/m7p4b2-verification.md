# M7-P4b2 Verification: Owner AI Review Rule Editor

## 1. Status

M7-P4b2 completes the owner-facing AI review rule editing surface on top of
the P4b1 backend rule foundation. It adds the owner task-detail entry point,
the append-only rule save form, the first-class rule read contract, the version
history panel, and publish/current-rule feedback.

Final anchors after C4/C5 closure:

| Anchor | Value |
|---|---|
| OpenAPI MD5 | `1acd96fb6c0fd0e7b084245d8ae3fa76` |
| Frontend Vitest | `147` |
| Backend tests | `549 / 88` in escalated run |
| Migrations | `17` |
| humanpending | `157` after this update |

The prior P4b1 OpenAPI anchor `b7df19fdb69f8d22b2f0dbdbc845d95d` was
retired in C1.5 when the read contract was added. The C1.5 anchor
`1acd96fb6c0fd0e7b084245d8ae3fa76` remains the P4b2 final OpenAPI anchor.

## 2. Goal

P4b1 delivered backend AI review rules but left the owner UI deferred. P4b2
closes that gap by giving owners a task-detail workflow for:

- opening the AI review rule editor;
- saving a new draft rule version from prompt, dimensions, and threshold;
- listing rule versions for the task;
- publishing a draft rule; and
- seeing which rule is currently active from the backend task pointer.

This is still configuration provenance, not an automated verdict path. ADR-005
remains intact: AI evidence does not replace human review accountability.

## 3. Commit Map

| Commit | Cluster | Purpose |
|---|---|---|
| `cca7ca3` | C1 gate | Owner task-detail rule-entry contract and frontend scope gate |
| `3ea1d58` | Read research | Option A read contract decision: first-class rule list endpoint |
| `5f3c4d2` | Read gate | C1.5 gate for list endpoint and `isCurrent` response metadata |
| `3851f41` | C1.5 | `GET /ai-review/rules?taskId=...`, `AiReviewRule.isCurrent`, write-endpoint error refs |
| `da10e1e` | C2 | Owner editor entry, save/publish hooks, append-only save form, client validation |
| `62af1f6` | C3 | Rule history panel, list query hook, publish flow, list invalidation |
| (this commit) | C4/C5 | Integration guard, verification doc, humanpending closure |

## 4. Contract And Anchor Chain

| Step | OpenAPI MD5 | Migrations | Why |
|---|---|---:|---|
| P4b1 final | `b7df19fdb69f8d22b2f0dbdbc845d95d` | `17` | Backend rule runtime binding |
| C1.5 | `1acd96fb6c0fd0e7b084245d8ae3fa76` | `17` | Rule list read contract and `isCurrent` |
| C2 / C3 / C4 / C5 | `1acd96fb6c0fd0e7b084245d8ae3fa76` | `17` | Pure frontend and verification work |

C1.5 intentionally changed the OpenAPI contract. C2 and C3 did not regenerate
or alter the contract; they consume the generated types from C1.5.

## 5. Cluster Delivery

### 5.1 C1: Entry Contract

C1 defined the first UI landing point: the owner task detail page gains an AI
review rule entry card. The final implementation is carried by the C2 frontend
commit, alongside the save/publish hooks it needs.

The entry point does not imply a current rule. Active state is deliberately left
to the later read contract and history panel.

### 5.2 C1.5: Read Contract

C1.5 made AI review rules readable as a first-class resource:

- `GET /ai-review/rules?taskId=...`;
- 200 response as `AiReviewRule[]`;
- 400/401/403/404 error refs;
- required `AiReviewRule.isCurrent`;
- save/publish endpoint error refs completed.

`isCurrent` is response metadata derived from `tasks.current_ai_review_rule_id`.
It is not a persisted rule column and is not derived from `status=published`.

C1.5 preserved the existing `selectByTaskId` DESC query and added a dedicated
ASC list query for UI history. Contract tests guard both directions.

### 5.3 C2: Save Form

C2 added the owner editor form:

- prompt textarea;
- dimensions dynamic list;
- threshold numeric input;
- client-side UX validation mirroring backend messages;
- save through `useSaveAiReviewRuleMutation`;
- append-only behavior, where each save creates a new draft rule version.

The client validation is not authoritative. Backend 4xx responses remain the
source of truth and are displayed through the hook's user-facing error mapping.

C2 does not consume the list endpoint, does not publish, and does not present
active/current state.

### 5.4 C3: History And Publish

C3 added the rule history panel and publish path:

- `useListAiReviewRulesQuery(taskId)`;
- history list by `versionNo`;
- current badge only when backend `isCurrent=true`;
- draft publish button using `usePublishAiReviewRuleMutation`;
- publish success invalidates the rule-list query instead of locally mutating
  current state.

The UI does not call a detail endpoint. The list endpoint is sufficient for
history, publish actions, and current-state display in v1.

### 5.5 C4/C5: Closure

C4/C5 adds a permanent frontend integration guard proving the save form and
history panel remain scoped separately:

- save form still contains prompt/save controls;
- history contains version history, current-state marker, and publish controls;
- the form section does not absorb publish/current behavior.

The backend full test suite was re-run outside the sandbox after the sandbox
run reproduced the old socket/MySQL errors. The escalated run passed:
`549` tests, `0` errors, `88` skipped.

Browser three-viewport verification remains a D-port item. The in-app browser
automation endpoint returned `Browser is not available: iab`, so no truthful
1440/1280/1024 screenshots could be captured in this session.

## 6. Key Invariants

### Append-Only Rules

The editor saves new draft rule versions. It does not update an existing draft
in place and it does not prefill from historical rules.

### Current Rule Source

`isCurrent` comes only from the backend list response. The frontend does not
derive current state from `status=published`. Multiple published versions may
exist, but only the task pointer can make one current.

### Publish Refresh

Publish success invalidates `['ai-review-rules', taskId]` and waits for the
backend list response to recalculate current state. The frontend does not
optimistically rewrite `isCurrent`.

### No Detail Endpoint In V1

P4b2 does not add or consume `GET /ai-review/rules/{ruleId}`. The endpoint is
deferred until deep-linking or single-version inspection needs it.

### ADR Boundaries

ADR-005 remains intact: this UI edits AI review configuration and provenance,
not automated final verdicts. ADR-011 remains intact: provider-specific prompts
and adapters stay in `services/agent`; owner business prompts stay in LabelHub
rule/prompt assets.

## 7. Verification

Frontend targeted coverage:

```text
AiReviewRuleEditorIntegration.test.tsx
```

It verifies that save-form controls remain separated from history/current/publish
controls.

Frontend full test result:

```text
24 files / 147 tests passed
```

Backend sandbox run:

```text
549 tests, 10 errors, 88 skipped
```

All 10 errors were socket/database/provider-connectivity errors. The same suite
passed in an escalated run:

```text
549 tests, 0 errors, 88 skipped
```

This closes the C1.5 D-port backend error watch for this workspace.

## 8. R8 And Deferred Items

- Three-viewport browser screenshots are still D-port because Browser runtime
  was unavailable (`Browser is not available: iab`).
- Detail endpoint remains deferred. List response contains all fields required
  by v1 history and publish UI.
- No task contract change was made; current-state display stays localized to
  `AiReviewRule.isCurrent`.
