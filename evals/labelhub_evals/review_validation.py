"""Python mirror of services/api AiReviewOutputValidator (ADR-019).

The single source of behaviour is packages/contracts/fixtures/review-output-validation-corpus.json;
tests/test_review_validation.py runs this module against every case, and the Java implementation
runs the same corpus. Business authority stays in services/api; this mirror exists so offline
evals can run the H2 loop without an API server.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from decimal import ROUND_HALF_UP, Decimal

RULES_VERSION = "review-output-v1"
REQUIRED = ("overallSuggestion", "confidence", "summary", "dimensionScores", "fieldFindings")
SUGGESTIONS = ("pass", "reject", "manual_review")
SEVERITIES = ("info", "warning", "error")


@dataclass(frozen=True)
class ValidationContext:
    dimensions: tuple[str, ...]
    pass_threshold: Decimal
    reject_threshold: Decimal
    stable_ids: frozenset[str]

    @staticmethod
    def from_json(data: dict) -> "ValidationContext":
        return ValidationContext(tuple(data["dimensions"]), Decimal(data["passThreshold"]), Decimal(data["rejectThreshold"]), frozenset(data["stableIds"]))


@dataclass(frozen=True)
class ValidationError:
    code: str
    path: str
    message: str

    def to_json(self) -> dict:
        return {"code": self.code, "message": self.message, "path": self.path}


def _is_number(v) -> bool:
    return isinstance(v, (int, float)) and not isinstance(v, bool)


def _unit(v) -> bool:
    return _is_number(v) and 0 <= v <= 1


def _nonblank(v) -> bool:
    return isinstance(v, str) and v.strip() != ""


def _structure(output) -> list[ValidationError]:
    err = lambda path, msg: ValidationError("invalid_structure", path, msg)  # noqa: E731
    if not isinstance(output, dict):
        return [err("$", "record_ai_review arguments must be a JSON object")]
    errors = [err(k, f"missing required field {k}") for k in REQUIRED if k not in output]
    if errors:
        return errors
    if output["overallSuggestion"] not in SUGGESTIONS:
        errors.append(err("overallSuggestion", "overallSuggestion must be one of pass, reject, manual_review"))
    if not _unit(output["confidence"]):
        errors.append(err("confidence", "confidence must be a number in [0, 1]"))
    if not isinstance(output["summary"], str):
        errors.append(err("summary", "summary must be a string"))
    dims = output["dimensionScores"]
    if not isinstance(dims, list):
        errors.append(err("dimensionScores", "dimensionScores must be an array"))
    else:
        for i, row in enumerate(dims):
            if not isinstance(row, dict):
                errors.append(err(f"dimensionScores[{i}]", "dimension score must be an object"))
                continue
            if not _nonblank(row.get("dimension")):
                errors.append(err(f"dimensionScores[{i}].dimension", "dimension must be a non-empty string"))
            if not _unit(row.get("score")):
                errors.append(err(f"dimensionScores[{i}].score", "score must be a number in [0, 1]"))
    findings = output["fieldFindings"]
    if not isinstance(findings, list):
        errors.append(err("fieldFindings", "fieldFindings must be an array"))
    else:
        for i, row in enumerate(findings):
            base = f"fieldFindings[{i}]"
            if not isinstance(row, dict):
                errors.append(err(base, "field finding must be an object"))
                continue
            if not _nonblank(row.get("fieldPath")):
                errors.append(err(base + ".fieldPath", "fieldPath must be a non-empty string"))
            if row.get("severity") not in SEVERITIES:
                errors.append(err(base + ".severity", "severity must be one of info, warning, error"))
            if not isinstance(row.get("finding"), str):
                errors.append(err(base + ".finding", "finding must be a string"))
            if row.get("stableId") is not None and not isinstance(row.get("stableId"), str):
                errors.append(err(base + ".stableId", "stableId must be a string or null"))
            if row.get("confidence") is not None and not _unit(row.get("confidence")):
                errors.append(err(base + ".confidence", "confidence must be a number in [0, 1] or null"))
    return errors


def _clamp(v: Decimal) -> Decimal:
    return max(Decimal(0), min(Decimal(1), v))


def final_score(output: dict, dimensions: tuple[str, ...]) -> Decimal:
    """Same arithmetic as AiReviewScoringPolicy: first score per dimension, confidence fallback,
    clamp to [0,1], equal-weight mean rounded to 4 decimals HALF_UP."""
    provided: dict[str, Decimal] = {}
    for row in output["dimensionScores"]:
        provided.setdefault(row["dimension"], _clamp(Decimal(str(row["score"]))))
    fallback = _clamp(Decimal(str(output["confidence"])))
    scores = [provided.get(d, fallback) for d in dimensions]
    return (sum(scores, Decimal(0)) / Decimal(len(scores))).quantize(Decimal("0.0001"), rounding=ROUND_HALF_UP)


def zone(score: Decimal, ctx: ValidationContext) -> str:
    if score >= ctx.pass_threshold:
        return "pass"
    if score <= ctx.reject_threshold:
        return "reject"
    return "manual_review"


def _root(field_path: str) -> str:
    return re.split(r"[.\[]", field_path, maxsplit=1)[0]


# ADR-019 v2: what the loop may ask the model to fix (form) vs. what it only flags (judgement).
CORRECTABLE = frozenset({"invalid_structure", "missing_dimension", "duplicate_dimension", "unknown_dimension", "unknown_field"})
FLAG_ONLY = frozenset({"suggestion_score_conflict", "error_finding_on_pass"})


def routing(output, ctx: ValidationContext) -> tuple[str | None, Decimal | None]:
    """(zone, final score) the API's scoring authority assigns this output; (None, None) when the output is
    not structurally valid. Returned by validate-review so callers never re-implement the arithmetic."""
    if _structure(output):
        return None, None
    score = final_score(output, ctx.dimensions or ("overall",))
    return zone(score, ctx), score


def validate(output, ctx: ValidationContext) -> list[ValidationError]:
    errors = _structure(output)
    if errors:
        return errors
    dimensions = ctx.dimensions or ("overall",)
    seen: set[str] = set()
    for i, row in enumerate(output["dimensionScores"]):
        name = row["dimension"]
        path = f"dimensionScores[{i}].dimension"
        if name in seen:
            errors.append(ValidationError("duplicate_dimension", path, f"dimension {name} is scored more than once"))
            continue
        seen.add(name)
        if name not in dimensions:
            errors.append(ValidationError("unknown_dimension", path, f"dimension {name} is not configured; use only: {', '.join(dimensions)}"))
    for name in dimensions:
        if name not in seen:
            errors.append(ValidationError("missing_dimension", "dimensionScores", f"configured dimension {name} has no score"))
    known = ", ".join(sorted(ctx.stable_ids)) or "(none)"
    for i, row in enumerate(output["fieldFindings"]):
        sid = row.get("stableId")
        if sid is not None and sid not in ctx.stable_ids:
            errors.append(ValidationError("unknown_field", f"fieldFindings[{i}].stableId", f"stableId {sid} does not exist; known fields: {known}"))
        root = _root(row["fieldPath"])
        if root not in ctx.stable_ids:
            errors.append(ValidationError("unknown_field", f"fieldFindings[{i}].fieldPath", f"fieldPath {row['fieldPath']} does not start with a known field; known fields: {known}"))
    score = final_score(output, dimensions)
    expected = zone(score, ctx)
    if output["overallSuggestion"] != expected:
        errors.append(ValidationError(
            "suggestion_score_conflict", "overallSuggestion",
            f"overallSuggestion {output['overallSuggestion']} contradicts the scores: final score {score} routes to {expected} "
            f"(pass >= {ctx.pass_threshold}, reject <= {ctx.reject_threshold}); change the suggestion or the scores so they agree",
        ))
    if output["overallSuggestion"] == "pass":
        for i, row in enumerate(output["fieldFindings"]):
            if row["severity"] == "error":
                errors.append(ValidationError("error_finding_on_pass", f"fieldFindings[{i}].severity", "a pass suggestion cannot carry an error-severity finding"))
    return errors
