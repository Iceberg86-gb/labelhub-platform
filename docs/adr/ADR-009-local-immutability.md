# ADR-009 Local Immutability

## Status

Accepted

## Decision

LabelHub does not use full event sourcing. Only four evidence-critical objects are immutable or append-only: schema versions, submissions, quality ledger entries, and export snapshots.

Task, session, and user records remain current-state CRUD objects.

## Consequences

- The project avoids turning every business change into an event stream.
- The credibility chain for supervised training data stays reproducible.
- Engineering cost is contained to a small number of write paths.
