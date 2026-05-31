# ADR-011 Doubao Default With OpenAI Fallback

## Status

Accepted, revised by Backend Batch B.

## Decision

The AI worker uses a registry-first runtime provider source for automatic AI review.

Runtime resolution rules:

- If the task owner has exactly one enabled DB-backed LLM provider config, the agent reads that config, decrypts the stored provider secret locally with `LABELHUB_LLM_PROVIDER_MASTER_KEY`, and calls the configured OpenAI-compatible endpoint.
- If the task owner has no enabled DB-backed provider config, the agent falls back to the existing env/config provider settings.
- If the task owner has multiple enabled DB-backed provider configs, runtime resolution fails with a configuration error; Batch B does not introduce provider priority/order schema.
- If the DB-backed provider config has a permanent configuration problem, auth failure, missing secret, or decrypt failure, the agent does not silently fall back to env/config. The failure must be visible through the existing worker retry/dead-letter path.
- Transient provider network/5xx/timeout failures are handled by the existing worker retry policy in Batch B v1; env fallback after transient exhaustion is deferred until it can be proven not to hide permanent configuration errors.

Doubao is represented as OpenAI-compatible when the configured endpoint supports the same chat-completions contract. OpenAI and fake/local providers remain behind the agent provider abstraction for fallback and local development.

ADR-005 remains unchanged: AI output is auxiliary evidence only, and the human review verdict is still the final adjudication source.

## Consequences

- The demo can use the required course resource.
- Owners can configure runtime provider credentials without changing AI review business modules.
- Provider-specific prompts, model names, output adapters, and runtime source selection stay in `services/agent`.
- The agent now holds `LABELHUB_LLM_PROVIDER_MASTER_KEY` outside local/fake mode, so secret redaction and no-leak tests are mandatory for any future runtime provider changes.
- Provider-order/fallback-chain schema is intentionally deferred; Batch B v1 supports one enabled DB provider per owner.
