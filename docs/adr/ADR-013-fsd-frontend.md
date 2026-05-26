# ADR-013 Simplified Feature-Sliced Frontend

## Status

Accepted

## Decision

The web app uses a simplified Feature-Sliced structure: `app`, `pages`, `features`, `entities`, and `shared`.

## Consequences

- Page composition and business actions stay separate.
- The structure is understandable for a personal project.
- More packages are deferred until a second frontend exists.
