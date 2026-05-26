# ADR-008 Outbox Pattern

## Status

Accepted

## Decision

Submission, review, AI, and export side effects are queued through a MySQL `outbox` table with polling workers.

## Consequences

- API transactions can atomically write business state and async events.
- No Kafka or external queue is needed in Stage 1.
- Workers need idempotency keys, retry counters, and dead-letter handling.
