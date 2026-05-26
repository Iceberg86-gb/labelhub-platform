# ADR-006 Function Calling Required

## Status

Accepted

## Decision

AI pre-review requires structured output through function-calling compatible JSON. Plain text parsing is not accepted for verdict decisions.

## Consequences

- Retry and fail-to-human behavior is deterministic when output validation fails.
- Review dimensions remain machine-readable.
- Provider adapters must normalize model-specific tool/function output into one internal schema.
