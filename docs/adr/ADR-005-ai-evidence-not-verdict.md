# ADR-005 AI Evidence Does Not Directly Own Verdict

## Status

Accepted

## Decision

AI calls produce structured evidence and provenance. They do not directly overwrite the final dataset verdict.

## Consequences

- Human review remains the accountable gate for accepted training data.
- AI output can be exported, inspected, or excluded.
- The API must persist prompt, model, hash, cost, and result trace for every call.
