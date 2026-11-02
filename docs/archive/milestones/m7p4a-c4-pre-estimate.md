# M7-P4a C4 Pre-Estimate: Provenance Display Enhancement

## Status

Pre-estimate gate. Awaiting adjudication before implementation.

## Scope Decision

C4 should remain a pure additive frontend display cluster.

It will display the evidence fields already available on `AiCall`:

- `promptVersionId`
- `providerAdapterVersion`

It will not change the trigger flow, backend writes, OpenAPI contract, generated types, migrations, or C1-C3 service behavior.

## Display Recommendation

Recommendation: split metadata rows instead of an inline value.

### Why Not Inline

An inline format like `Prompt: promptVersion#1 (#1) · adapter: agent-default-v1` is compact, but it becomes dense in the drawer and is easier to wrap awkwardly at 1024px.

### Recommended Format

`AiProvenanceCard`:

- Existing line: `Prompt: promptVersion#1`
- Add line: `Prompt ID: #1`
- Add line: `Adapter: agent-default-v1`

`AiReviewDrawer`:

- Existing `MetaItem`: `Prompt`
- New `MetaItem`: `Prompt ID`
- New `MetaItem`: `Adapter`

Legacy fallback:

- If `promptVersionId == null`, omit the Prompt ID row.
- Never render `#null`, `#undefined`, or an empty id.
- If `providerAdapterVersion` is absent despite the generated type, omit the adapter row rather than inventing a client-side fallback.

## Tests

Use the existing frontend test style and avoid new dependencies.

Planned tests:

1. `AiProvenanceCard` renders prompt id and adapter for a modern `AiCall`.
2. `AiProvenanceCard` omits prompt id for legacy `promptVersionId: null`.
3. `AiReviewDrawer` renders prompt id and adapter for a modern result.
4. `AiReviewDrawer` omits prompt id for legacy `promptVersionId: null`.

Implementation options:

- Prefer `renderToString` smoke tests, matching existing Formily and schema-design test style.
- If `AiProvenanceCard` query mocking is brittle, extract a tiny presentational helper only if it keeps production code clearer. Do not add React Testing Library.

## Frozen Boundaries

C4 must not modify:

- `packages/contracts/openapi/labelhub.yaml`
- `apps/web/src/shared/api/generated/schema.d.ts`
- `apps/web/src/features/ai/useTriggerAiReviewMutation.ts`
- `apps/web/src/features/ai/useDefaultPromptVersionQuery.ts`
- `apps/web/src/pages/owner/OwnerSubmissionPage.tsx`
- Any backend file
- Any migration
- `humanpending.md`
- P3a/P3b validation, linkage, renderer, or corpus files

## Verification

Required implementation report checks:

- `pnpm --filter @labelhub/web typecheck`: pass
- `pnpm --filter @labelhub/web test`: pass, frontend count rises from `127`
- `pnpm --filter @labelhub/web build`: pass
- OpenAPI MD5 remains `23a67e2cad632b3e9cfaff03c5d05dd7`
- Migrations remain `14`
- humanpending remains `141`
- Backend git diff is empty
- Trigger-path git diff is empty

## Three-Viewport Plan

If browser automation is available:

- Capture 1440, 1280, and 1024 viewport checks for provenance card/drawer with id + adapter visible.
- Verify no overflow and no `#null` on legacy rows.

If browser automation is unavailable:

- Use D-口径: component tests verify rendered strings and fallback behavior; CSS/layout risk is low because C4 only adds short metadata text and does not change layout containers.

## Estimate

Hand-authored estimate:

| File / area | Estimate |
|---|---:|
| `AiProvenanceCard.tsx` | 35 |
| `AiReviewDrawer.tsx` | 25 |
| `AiProvenanceCard.test.tsx` | 70 |
| `AiReviewDrawer.test.tsx` | 70 |
| Total | 200 |

Recommended cap:

- Soft cap: `300`
- Hard cap: `450`

## Adjudication Items

1. Approve split-row display format.
2. Approve legacy fallback: omit Prompt ID when `promptVersionId` is null.
3. Approve no new dependencies; use `renderToString` style tests or a tiny presentational helper if needed.
4. Approve cap `300 / 450`.
5. Confirm C4 remains frontend display only, with provenance display enhancement but no trigger/backend/contract changes.
