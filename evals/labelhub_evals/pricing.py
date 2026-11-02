"""Model pricing, read from the API's application.yml (labelhub.ai.pricing.models) so the eval and production
cost figures come from one table."""

from __future__ import annotations

import re
from dataclasses import dataclass
from decimal import Decimal
from functools import cache

from labelhub_evals.paths import API_APPLICATION_YML


@dataclass(frozen=True)
class Price:
    input_cache_hit_per_1m: Decimal
    input_cache_miss_per_1m: Decimal
    output_per_1m: Decimal

    def cost(self, prompt_tokens: int, completion_tokens: int, cached_prompt_tokens: int = 0) -> Decimal:
        miss = max(prompt_tokens - cached_prompt_tokens, 0)
        million = Decimal(1_000_000)
        return (
            Decimal(cached_prompt_tokens) * self.input_cache_hit_per_1m
            + Decimal(miss) * self.input_cache_miss_per_1m
            + Decimal(completion_tokens) * self.output_per_1m
        ) / million


@cache
def price_table() -> dict[str, Price]:
    lines = API_APPLICATION_YML.read_text(encoding="utf-8").splitlines()
    table: dict[str, Price] = {}
    in_models, model_indent, current, fields = False, None, None, {}

    def flush():
        if current and {"input-cache-hit-per-1m-usd", "input-cache-miss-per-1m-usd", "output-per-1m-usd"} <= fields.keys():
            table[current] = Price(
                Decimal(fields["input-cache-hit-per-1m-usd"]),
                Decimal(fields["input-cache-miss-per-1m-usd"]),
                Decimal(fields["output-per-1m-usd"]),
            )

    for raw in lines:
        if not raw.strip() or raw.strip().startswith("#"):
            continue
        indent = len(raw) - len(raw.lstrip())
        stripped = raw.strip()
        if stripped == "models:" and in_models is False:
            in_models, model_indent = True, None
            continue
        if not in_models:
            continue
        if model_indent is None:
            model_indent = indent
        if indent < model_indent:
            break
        if indent == model_indent and stripped.endswith(":"):
            flush()
            current, fields = stripped[:-1], {}
        else:
            match = re.match(r"([\w-]+):\s*([0-9.]+)", stripped)
            if match:
                fields[match.group(1)] = match.group(2)
    flush()
    return table
