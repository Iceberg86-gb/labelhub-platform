# ADR-004 Export Snapshot

## Status

Accepted

## Decision

Every export creates an immutable snapshot with file hash, schema version list, verdict rule version, data scope, field mapping snapshot, and canonicalization version.

## Consequences

- Two exports can be compared and traced to schema, rule, or data changes.
- Hash stability depends on deterministic canonicalization.
- Export jobs must store parameters, not only generated files.
