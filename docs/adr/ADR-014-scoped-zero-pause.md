# ADR-014 Scoped Zero Pause

## Status

Proposed

## Context

The project uses `docs/process/humanpending.md` as a visible queue for unresolved decisions. This prevents the agent from silently making broad architecture changes just to keep moving.

## Decision

When a business rule cannot be safely inferred, record a scoped item in `docs/process/humanpending.md` instead of blocking unrelated implementation work.

## Consequences

- Development keeps moving while preserving unresolved decisions.
- Pending items are visible at the project root.
- The file must be reviewed before final defense packaging.
