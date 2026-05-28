# M7-P4a C4 Scope Budget: Provenance Display Enhancement

## Status

Pre-estimate gate. No production code in this cluster until adjudication.

Anchor after C3:

- HEAD: `5ca4e2d`
- OpenAPI MD5: `23a67e2cad632b3e9cfaff03c5d05dd7`
- Backend tests: `515 / 83`
- Frontend Vitest: `127`
- Migrations: `14`
- humanpending: `141`

## Goal

C4 is a pure additive frontend display cluster. C3 already writes `promptVersionId` and `providerAdapterVersion` into `AiCall`; C4 makes those evidence fields visible in provenance UI.

This cluster does not change trigger behavior, backend persistence, OpenAPI, migrations, or any P3a/P3b logic.

## Current Anchors

`apps/web/src/features/ai/AiProvenanceCard.tsx`

- `AiCallItem` currently renders `Prompt: {call.promptVersion}`.

`apps/web/src/features/ai/AiReviewDrawer.tsx`

- `AiReviewResultPanel` currently renders `<MetaItem label="Prompt" value={aiCall.promptVersion} />`.

Generated `AiCall` already includes:

- `promptVersion`: required legacy display label, currently `promptVersion#<versionNo>` for new C3 calls.
- `promptVersionId`: nullable number for legacy rows.
- `providerAdapterVersion`: required string, currently `agent-default-v1`.

## Proposed Display Format

Use split metadata instead of one dense inline string.

### AiProvenanceCard

Keep the existing `Prompt: <label>` line and add compact tertiary evidence lines:

- `Prompt ID: #<id>` only when `promptVersionId != null`
- `Adapter: <providerAdapterVersion>` when present

Legacy fallback: if `promptVersionId` is null, do not render `#null`; keep only the prompt label plus adapter if available.

### AiReviewDrawer

Keep the existing `Prompt` `MetaItem` and add:

- `Prompt ID` `MetaItem` only when `promptVersionId != null`
- `Adapter` `MetaItem` when present

This format is more scannable in the drawer grid, wraps more safely on 1024px, and keeps legacy rows clean.

## File Plan

Modify:

- `apps/web/src/features/ai/AiProvenanceCard.tsx`
  - Add display of prompt version id and provider adapter version.
  - No data fetching change.

- `apps/web/src/features/ai/AiReviewDrawer.tsx`
  - Add `MetaItem` rows for prompt id and adapter.
  - Preserve existing prompt label display.

Create tests:

- `apps/web/src/features/ai/AiProvenanceCard.test.tsx`
  - Render provenance item through a mocked query, or extract a tiny formatter/helper if that keeps the component test lean.
  - Assert id + adapter present for new rows.
  - Assert legacy null id does not render `#null`.

- `apps/web/src/features/ai/AiReviewDrawer.test.tsx`
  - Use `renderToString` style already used in the repo.
  - Assert id + adapter present for new rows.
  - Assert legacy null fallback.

## Forbidden Surfaces

- Backend code
- `packages/contracts/openapi/labelhub.yaml`
- Generated types
- Trigger path: `useTriggerAiReviewMutation`, `OwnerSubmissionPage`, `useDefaultPromptVersionQuery`
- C1/C2/C3 service and persistence code
- P3a/P3b runtime validation, linkage, renderer, corpus files
- Migrations
- humanpending

## Verification Plan

- `pnpm --filter @labelhub/web typecheck`
- `pnpm --filter @labelhub/web test`
- `pnpm --filter @labelhub/web build`
- OpenAPI MD5 remains `23a67e2cad632b3e9cfaff03c5d05dd7`
- Migrations remain `14`
- humanpending remains `141`
- Backend diff remains empty
- Three viewport manual check: 1440 / 1280 / 1024, or D-口径 if browser automation is unavailable.

## Estimate

Hand-authored estimate:

| Area | Estimate |
|---|---:|
| AiProvenanceCard display | 35 |
| AiReviewDrawer display | 25 |
| Component/helper tests | 140 |
| Visual/D-口径 notes in implementation report | 10 |
| Total | 210 |

Recommended cap:

- Soft cap: `300`
- Hard cap: `450`

Generated churn: none expected.
