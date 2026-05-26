# ADR-012 Contract-First OpenAPI

## Status

Accepted

## Decision

`packages/contracts/openapi/labelhub.yaml` is the API contract source. Java interfaces and TypeScript client types are generated from it.

## Consequences

- Frontend and backend share one API language without sharing runtime code.
- Generated source is ignored by Git.
- API changes start from contract review before implementation.
