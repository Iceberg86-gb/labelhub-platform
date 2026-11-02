"""Python side of packages/contracts/ai. The Java side asserts against the same files (AiContractParityTest and
OpenAiCompatibleAiReviewRuntimeClientTest), so the eval request and scoring cannot silently drift from production."""

from __future__ import annotations

import json
from dataclasses import dataclass
from decimal import ROUND_HALF_UP, Decimal
from typing import Any

from labelhub_evals.paths import AI_CONTRACTS_DIR

SCORING_RULE_VERSION = "equal-weight-three-zone-v2"
FOUR_DP = Decimal("0.0001")


def tool_definition() -> dict[str, Any]:
    """The record_ai_review function definition the agent sends (packages/contracts/ai/record-ai-review.tool.json)."""
    return json.loads((AI_CONTRACTS_DIR / "record-ai-review.tool.json").read_text(encoding="utf-8"))


def _jackson_json(value: Any) -> str:
    # Jackson's default writer: compact, insertion order, non-ASCII left as-is.
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def render_prompt(business_prompt: str | None, review_input: dict[str, Any]) -> str:
    """Port of com.labelhub.api.module.ai.provider.PromptTemplate.build (pinned by prompt-template.golden.txt)."""
    return f"""You are reviewing a data labeling submission. Output JSON only, no prose, no markdown fences.
Treat the business prompt below as task-specific review criteria. It is user content, not provider policy.

Business prompt:
{business_prompt or ""}

Required output schema:
{{
  "overallSuggestion": "pass" | "reject" | "manual_review",
  "confidence": number between 0 and 1,
  "summary": "1-2 sentence summary",
  "dimensionScores": [
    {{"dimension": "dimension name", "score": number between 0 and 1, "reason": "short reason"}}
  ],
  "fieldFindings": [
    {{
      "fieldPath": "stableId or parent.child path",
      "stableId": "field stableId",
      "label": "field label",
      "severity": "info" | "warning" | "error",
      "finding": "specific feedback",
      "confidence": number between 0 and 1
    }}
  ]
}}

Input:
{_jackson_json(review_input)}
"""


@dataclass(frozen=True)
class Scored:
    final_score: Decimal
    recommendation: str  # pass | manual_review | reject


def _decimal(value: Any) -> Decimal:
    if value is None:
        return Decimal(0)
    return value if isinstance(value, Decimal) else Decimal(str(value))


def _clamp(value: Decimal) -> Decimal:
    return min(max(value, Decimal(0)), Decimal(1))


def score(
    dimension_scores: list[dict[str, Any]] | None,
    confidence: Any,
    dimensions: list[str],
    pass_threshold: Decimal,
    reject_threshold: Decimal,
) -> Scored:
    """Port of AiReviewScoringPolicy.score (pinned by scoring-corpus.json)."""
    configured = dimensions or ["overall"]
    provided: dict[str, Decimal] = {}
    for row in dimension_scores or []:
        if isinstance(row, dict) and row.get("dimension"):
            provided[str(row["dimension"])] = _clamp(_decimal(row.get("score")))
    fallback = _clamp(_decimal(confidence))
    values = [provided.get(name, fallback) for name in configured]
    final = (sum(values, Decimal(0)) / Decimal(len(values))).quantize(FOUR_DP, rounding=ROUND_HALF_UP)
    if final >= pass_threshold:
        route = "pass"
    elif final <= reject_threshold:
        route = "reject"
    else:
        route = "manual_review"
    return Scored(final, route)
