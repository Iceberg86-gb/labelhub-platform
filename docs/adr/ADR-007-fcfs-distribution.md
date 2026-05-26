# ADR-007 First-Come First-Served Distribution

## Status

Accepted

## Decision

Stage 1 uses first-come first-served task claiming. Claim writes use optimistic locking around remaining quota and item availability.

## Consequences

- The behavior is easy to demo and reason about.
- Owner assignment and quota bidding remain future extensions.
- Claimed sessions store a task snapshot so later task edits do not affect in-progress work.
