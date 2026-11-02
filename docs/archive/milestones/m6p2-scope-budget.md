# M6-P2 Scope Budget

## Theme

Owner setup UX repair: clean up the M6-P0 audit polish backlog and add task-detail next-step guidance without changing backend behavior.

## Scope Confirmed By User

| Item | Source | Type | Budget |
|------|--------|------|--------|
| Polish #001 Login autofill validation drift | M6-P0 audit | UI bug | ~15 lines |
| Polish #002 Owner task list created-time `-` | M6-P0 audit | UI bug | ~15 lines |
| Bug #003 Schema creation discoverability | M6-P0 audit | UI gap -> next-step guidance | ~80 lines |
| Product Boundary #001 Repeat claim semantics | M6-P0 audit + Q9=A | UI copy | ~25 lines |

Total estimate: ~135 functional lines, below the 500-line budget.

## Anti-Scope-Creep Boundaries

M6-P2 next-step guidance is intentionally limited:

- Only inside the task detail page, not dashboard, sidebar, or other setup surfaces.
- CTA scope is capped at schema setup, dataset setup, and publish.
- No onboarding/tutorial concept, tooltip wizard, or step-indicator product flow.
- No new API endpoints, OpenAPI changes, or migrations.
- All CTAs use existing routes or existing page controls.
- State display is derived from existing task, schema, dataset, quota, and status fields.

Stop conditions:

- If any single item exceeds 100 functional lines, push back and re-scope.
- If next-step guidance grows beyond three CTAs, push back.
- If polish requires backend changes, defer to a follow-up phase.

## Strict-Constraint Exceptions

| Exception | Location | Justification |
|-----------|----------|---------------|
| Polish #001 | `LoginPage.tsx` autofill validation | UI bug fix, no contract change |
| Polish #002 | Owner task list created-time render | UI fallback display, no contract change |
| Bug #003 | Task detail page next-step CTA card | Additive UI, no backend or contract change |
| Product Boundary #001 | Labeler marketplace claim copy | UI copy clarifying Q9=A item-scoped claim semantics |

All exceptions are UI-only. Backend, OpenAPI, and migrations stay untouched.

## Commit Granularity

1. `docs: scope M6-P2 owner setup UX repair`
2. `test: add UX repair regression contracts`
3. `fix: repair login autofill validation`
4. `fix: render owner task created-time fallback`
5. `feat: add task detail next-step guidance`
6. `docs: record M6-P2 verification`
