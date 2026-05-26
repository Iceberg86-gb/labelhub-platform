# ADR-003 Quality Ledger

## Status

Accepted

## Decision

AI review and human review actions are stored as append-only evidence in `quality_ledger_entries`. `current_verdicts` is a derived and cacheable view, not the source of truth.

## Consequences

- Changing adjudication rules can re-derive historical verdicts without mutating evidence.
- Review history remains auditable.
- Rule upgrades need batch and lazy re-derivation paths.
