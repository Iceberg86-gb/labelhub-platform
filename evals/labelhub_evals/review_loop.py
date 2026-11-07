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
from labelhub_evals.review_validation import CORRECTABLE, ValidationContext, ValidationError, routing, validate

FEEDBACK_VERSION = "h2-validate-loop-v2"
HEADER = "Your record_ai_review call failed validation:"
FOOTER = ("Call record_ai_review again with a complete, corrected result. Fix exactly the problems listed: only reference fields "
          "that exist, and score every configured dimension exactly once. Keep your judgement of the submission unchanged: "
          "the same overallSuggestion and the same scores.")
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


def text_style_message(review_input: dict, attempts: list[Attempt], precedent_block: str | None = None) -> str:
    """Fallback for providers that reject replayed tool calls: one user turn quoting the previous call."""
    parts = [review.user_message(review_input, precedent_block)]
    for i, attempt in enumerate(attempts, 1):
        parts.append(f"\nYour record_ai_review call #{i} was:\n{attempt.arguments}\n{attempt.feedback}")
    return "\n".join(parts)


@dataclass
class LoopResult:
    """outcome:
    valid            first answer passed every rule
    corrected        correctable errors fixed, nothing flagged
    flagged          no correctable errors left, but a judgement inconsistency (suggestion/score conflict,
                     error finding on pass) is flagged: deterministic manual_review, never sent to the model
    downgraded       correctable errors still present after max_steps
    judgement_changed a correction moved the routing zone; the correction is discarded and the first
                     answer's judgement is kept (downgraded)
    correction_failed a correction turn produced no usable tool call; the first answer is kept (downgraded)
    format_violation no parseable tool call (not looped, as in production)
    `final` is the response whose output is reported; `final_errors` are the errors used to downgrade it."""

    final: ProviderResponse
    responses: list[ProviderResponse]
    step_errors: list[list[ValidationError]] = field(default_factory=list)
    outcome: str = "valid"
    final_errors: list[ValidationError] = field(default_factory=list)

    @property
    def steps(self) -> int:
        return len(self.responses)

    @property
    def downgrades(self) -> bool:
        return self.outcome in ("flagged", "downgraded", "judgement_changed", "correction_failed")


def _parse(response: ProviderResponse):
    """Parsed arguments, or None when the model did not produce a JSON tool call. Like production, a
    missing/unparseable tool call is a format failure, not something the loop corrects."""
    if response.violation is not None or response.arguments is None:
        return None
    try:
        return json.loads(response.arguments)
    except json.JSONDecodeError:
        return None


def run_loop(recorder, input_hash: str, review_input: dict, ctx: ValidationContext, max_steps: int, style: str = "tool",
             precedents=None) -> LoopResult:
    if max_steps < 1:
        raise ValueError("max_steps must be >= 1")
    first = recorder.review(input_hash, review_input, precedents)
    responses = [first]
    step_errors: list[list[ValidationError]] = []
    attempts: list[Attempt] = []
    first_output = _parse(first)
    if first_output is None:
        return LoopResult(first, responses, step_errors, "format_violation")
    first_zone, _ = routing(first_output, ctx)
    response, output = first, first_output
    while True:
        errors = validate(output, ctx)
        step_errors.append(errors)
        zone_now, _ = routing(output, ctx)
        if len(responses) > 1 and first_zone is not None and zone_now != first_zone:
            # Corrections may fix form, never judgement: keep the first answer, downgraded.
            return LoopResult(first, responses, step_errors, "judgement_changed", step_errors[0])
        correctable = [e for e in errors if e.code in CORRECTABLE]
        if not correctable:
            if not errors:
                return LoopResult(response, responses, step_errors, "valid" if len(responses) == 1 else "corrected")
            return LoopResult(response, responses, step_errors, "flagged", errors)
        if len(responses) >= max_steps:
            return LoopResult(response, responses, step_errors, "downgraded", errors)
        attempts.append(Attempt(response.arguments, tuple(correctable)))
        response = recorder.correct(input_hash, review_input, list(attempts), style, precedents)
        responses.append(response)
        output = _parse(response)
        if output is None:
            # A failed correction turn must not discard the already-validated first answer.
            return LoopResult(first, responses, step_errors, "correction_failed", step_errors[0])


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
