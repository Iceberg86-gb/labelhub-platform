from __future__ import annotations

from dataclasses import asdict, dataclass
from typing import Protocol


class ProviderError(RuntimeError):
    """Transport/auth/quota failure: nothing the model said. Never recorded, never scored as a model answer."""


@dataclass(frozen=True)
class ProviderResponse:
    """One model answer. `arguments` is the raw record_ai_review arguments string, or None when the model
    answered without the tool (`violation` then says why). Both cases are model behaviour and are recorded."""

    arguments: str | None
    violation: str | None
    input_tokens: int | None
    output_tokens: int | None
    latency_ms: int

    def to_json(self) -> dict:
        return asdict(self)

    @staticmethod
    def from_json(data: dict) -> "ProviderResponse":
        return ProviderResponse(
            data.get("arguments"), data.get("violation"), data.get("input_tokens"), data.get("output_tokens"), int(data["latency_ms"])
        )


class Provider(Protocol):
    @property
    def identity(self) -> str:
        """Stable model identity: provider/model[@effort]. Part of the replay cache key and run_id."""

    def review(self, review_input: dict) -> ProviderResponse: ...
