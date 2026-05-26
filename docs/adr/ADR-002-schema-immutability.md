# ADR-002 Schema Version Immutability

## Status

Accepted

## Decision

Published schema versions are immutable. A field stable ID cannot be reused after publication, and every submission stores the schema version used at submit time.

## Consequences

- Historical submissions can always be rendered and exported with their original schema.
- Schema evolution becomes explicit through new versions.
- Designers need a draft-to-publish workflow instead of editing published schemas in place.
