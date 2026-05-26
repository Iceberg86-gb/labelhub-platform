# ADR-001 Modular Monolith

## Status

Accepted

## Context

LabelHub is a personal defense project, not a production SaaS platform. The system still needs clear boundaries for tasks, schema, sessions, reviews, AI calls, exports, and audit logs.

## Decision

The backend uses a modular monolith in `services/api`. Business modules are separated by package boundaries under `module/`, while deployment remains a single Spring Boot API process.

`services/agent` is a separate worker process because AI review and field assistance consume outbox events asynchronously and can fail independently from request handling.

## Consequences

- The project keeps one database schema and one business authority boundary.
- Module boundaries remain visible without microservice overhead.
- Cross-module writes must go through service methods and audit/outbox helpers.
