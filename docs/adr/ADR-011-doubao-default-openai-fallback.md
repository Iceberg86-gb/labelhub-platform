# ADR-011 Doubao Default With OpenAI Fallback

## Status

Accepted

## Decision

The AI worker defaults to the course-provided Doubao endpoint. OpenAI and fake providers are implemented behind the same `LlmProvider` abstraction as fallback options.

## Consequences

- The demo can use the required course resource.
- Provider failures can fall back without changing business modules.
- Provider-specific prompts, model names, and output adapters stay in `services/agent`.
