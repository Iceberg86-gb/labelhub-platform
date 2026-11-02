"""System under test: the platform's current single-call AI pre-review (ADR-017 H1 baseline).

The request is built exactly like production: the API's buildInput shape, PromptTemplate rendering as one user
message, the record_ai_review tool forced via tool_choice, temperature 0. Parity is pinned by packages/contracts/ai.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from decimal import Decimal
from typing import Any

from labelhub_evals import contracts
from labelhub_evals.canonical import sha256_text
from labelhub_evals.datasets.helpsteer2 import Case
from labelhub_evals.paths import PROMPTS_DIR

SCHEMA_FIELDS = [{"stableId": "response", "label": "Response", "type": "textarea"}]


@dataclass(frozen=True)
class ReviewRule:
    """Mirrors an ai_review_rules row: business prompt + dimensions + three-zone thresholds."""

    prompt_name: str
    dimensions: tuple[str, ...] = ("helpfulness", "correctness")
    pass_threshold: Decimal = Decimal("0.80")
    reject_threshold: Decimal = Decimal("0.20")

    @property
    def business_prompt(self) -> str:
        return (PROMPTS_DIR / f"{self.prompt_name}.md").read_text(encoding="utf-8").strip()

    def describe(self) -> dict[str, Any]:
        return {
            "prompt": self.prompt_name,
            "promptSha256": sha256_text(self.business_prompt),
            "dimensions": list(self.dimensions),
            "passThreshold": str(self.pass_threshold),
            "rejectThreshold": str(self.reject_threshold),
            "scoringRuleVersion": contracts.SCORING_RULE_VERSION,
        }


def review_input(case: Case) -> dict[str, Any]:
    """Same keys and order as AiReviewService.buildInput."""
    return {
        "schemaVersionId": 1,
        "schemaFields": SCHEMA_FIELDS,
        "answerPayload": {"response": case.response},
        "datasetItemPayload": {"prompt": case.prompt},
        "task": {
            "id": 1,
            "title": "HelpSteer2 response review",
            "description": "Review whether the response is a good answer to the prompt.",
        },
        "submission": {"id": 1, "createdAt": None},
    }


def build_request(case: Case, rule: ReviewRule, model: str) -> dict[str, Any]:
    tool = contracts.tool_definition()
    return {
        "model": model,
        "messages": [{"role": "user", "content": contracts.render_prompt(rule.business_prompt, review_input(case))}],
        "temperature": 0,
        "tools": [{"type": "function", "function": tool}],
        "tool_choice": {"type": "function", "function": {"name": tool["name"]}},
    }


@dataclass
class Review:
    """What the agent would report back, plus how the platform would route it."""

    route: str  # pass | manual_review | reject (fail-to-human on any error, like the production path)
    final_score: Decimal | None
    overall_suggestion: str | None = None
    confidence: float | None = None
    dimension_scores: list[dict[str, Any]] = field(default_factory=list)
    error: str | None = None  # hard failure (format violation / provider error): routed to a human
    warnings: list[str] = field(default_factory=list)  # soft issues, e.g. a dimension that fell back to confidence


def parse_response(response: dict[str, Any], rule: ReviewRule) -> Review:
    """Same acceptance rules as OpenAiCompatibleAiReviewRuntimeClient: a record_ai_review tool call with JSON args."""
    try:
        calls = response["choices"][0]["message"].get("tool_calls") or []
    except (KeyError, IndexError, TypeError, AttributeError):
        return Review("manual_review", None, error="malformed_response")
    if not calls:
        return Review("manual_review", None, error="missing_tool_call")
    function = calls[0].get("function") or {}
    if function.get("name") != contracts.tool_definition()["name"]:
        return Review("manual_review", None, error="unexpected_function")
    try:
        args = json.loads(function.get("arguments") or "")
    except json.JSONDecodeError:
        return Review("manual_review", None, error="invalid_arguments_json")
    if not isinstance(args, dict):
        return Review("manual_review", None, error="invalid_arguments_json")
    dims = args.get("dimensionScores") if isinstance(args.get("dimensionScores"), list) else []
    scored = contracts.score(dims, args.get("confidence"), list(rule.dimensions), rule.pass_threshold, rule.reject_threshold)
    missing = [d for d in rule.dimensions if d not in {str(r.get("dimension")) for r in dims if isinstance(r, dict)}]
    return Review(
        scored.recommendation,
        scored.final_score,
        overall_suggestion=args.get("overallSuggestion"),
        confidence=args.get("confidence"),
        dimension_scores=dims,
        warnings=[f"dimension_fallback:{name}" for name in missing],
    )
