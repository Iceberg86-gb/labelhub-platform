"""H2 validate-and-correct loop for offline evals (ADR-019), mirroring services/agent AiReviewAgentLoop.

Step 1 is the unchanged H1 call (same prompt, same recording key), so H1 recordings are reused and
only correction turns cost new model calls. The correction prompt is byte-identical to the agent's
(both are pinned by packages/contracts/fixtures/review-loop-feedback.json).
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path

from labelhub_evals import review
from labelhub_evals.providers.base import ProviderResponse
from labelhub_evals.review_validation import ValidationContext, ValidationError, validate

FEEDBACK_VERSION = "h2-validate-loop-v1"
HEADER = "Your record_ai_review call failed validation:"
FOOTER = ("Call record_ai_review again with a complete, corrected result. Fix exactly the problems listed. "
          "Only reference fields that exist, score every configured dimension once, and make overallSuggestion agree with the zone "
          "your scores fall in. Change your judgement of the submission only if an error shows your own fields contradict each other.")
CORRECTION_STYLES = ("tool", "text")
FIXTURE = Path(__file__).resolve().parents[2] / "packages" / "contracts" / "fixtures" / "review-loop-feedback.json"


def render_feedback(errors: list[ValidationError]) -> str:
    return HEADER + "\n" + "".join(f"- [{e.code}] {e.path}: {e.message}\n" for e in errors) + FOOTER


@dataclass(frozen=True)
class Attempt:
    arguments: str
    errors: tuple[ValidationError, ...]

    @property
    def feedback(self) -> str:
        return render_feedback(list(self.errors))


def text_style_message(review_input: dict, attempts: list[Attempt]) -> str:
    """Fallback for providers that reject replayed tool calls: one user turn quoting the previous call."""
    parts = [review.user_message(review_input)]
    for i, attempt in enumerate(attempts, 1):
        parts.append(f"\nYour record_ai_review call #{i} was:\n{attempt.arguments}\n{attempt.feedback}")
    return "\n".join(parts)


@dataclass
class LoopResult:
    final: ProviderResponse
    responses: list[ProviderResponse]
    step_errors: list[list[ValidationError]] = field(default_factory=list)
    outcome: str = "valid"  # valid | corrected | downgraded | format_violation

    @property
    def steps(self) -> int:
        return len(self.responses)


def _parse(response: ProviderResponse):
    """Parsed arguments, or None when the model did not produce a JSON tool call. Like production, a
    missing/unparseable tool call is a format failure, not something the loop corrects."""
    if response.violation is not None or response.arguments is None:
        return None
    try:
        return json.loads(response.arguments)
    except json.JSONDecodeError:
        return None


def run_loop(recorder, input_hash: str, review_input: dict, ctx: ValidationContext, max_steps: int, style: str = "tool") -> LoopResult:
    if max_steps < 1:
        raise ValueError("max_steps must be >= 1")
    response = recorder.review(input_hash, review_input)
    responses = [response]
    step_errors: list[list[ValidationError]] = []
    attempts: list[Attempt] = []
    while True:
        output = _parse(response)
        if output is None:
            return LoopResult(response, responses, step_errors, "format_violation")
        errors = validate(output, ctx)
        step_errors.append(errors)
        if not errors:
            return LoopResult(response, responses, step_errors, "valid" if len(responses) == 1 else "corrected")
        if len(responses) >= max_steps:
            return LoopResult(response, responses, step_errors, "downgraded")
        attempts.append(Attempt(response.arguments, tuple(errors)))
        response = recorder.correct(input_hash, review_input, list(attempts), style)
        responses.append(response)


def downgraded_output(output: dict, errors: list[ValidationError]) -> dict:
    """Same deterministic downgrade as AiReviewAgentLoop: manual_review, findings on unknown fields dropped."""
    drop = set()
    for e in errors:
        if e.code == "unknown_field" and e.path.startswith("fieldFindings["):
            drop.add(int(e.path[len("fieldFindings["):].split("]", 1)[0]))
    out = dict(output)
    out["overallSuggestion"] = "manual_review"
    out["fieldFindings"] = [f for i, f in enumerate(output.get("fieldFindings") or []) if i not in drop]
    return out
