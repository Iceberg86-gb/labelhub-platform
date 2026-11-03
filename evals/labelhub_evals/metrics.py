"""H1 scorecard metrics (ADR-017 §3). Pure functions over per-item results.

Truth is binary (pass/reject). The AI routes into three zones; manual_review is "deferred to a human",
so agreement and kappa are measured on decided items and the deferral rate is reported alongside.
The primary risk metric is the auto-pass error rate: of items the AI would auto-pass, how many a
human would reject.
"""

from __future__ import annotations

import math
from decimal import Decimal


def _ratio(num: int, den: int) -> Decimal | None:
    return None if den == 0 else (Decimal(num) / Decimal(den)).quantize(Decimal("0.000001"))


def cohen_kappa(pairs: list[tuple[str, str]]) -> Decimal | None:
    """Binary Cohen's kappa over (truth, predicted) pairs. None when undefined (no items or p_e == 1)."""
    n = len(pairs)
    if n == 0:
        return None
    labels = ("pass", "reject")
    observed = sum(1 for t, p in pairs if t == p) / n
    expected = sum((sum(1 for t, _ in pairs if t == lab) / n) * (sum(1 for _, p in pairs if p == lab) / n) for lab in labels)
    if math.isclose(expected, 1.0):
        return None
    return Decimal(str((observed - expected) / (1 - expected))).quantize(Decimal("0.000001"))


def percentile(values: list[int], q: float) -> int | None:
    """Nearest-rank percentile (deterministic, no interpolation)."""
    if not values:
        return None
    ordered = sorted(values)
    rank = max(1, math.ceil(q / 100 * len(ordered)))
    return ordered[rank - 1]


def scorecard(results: list[dict], *, price_in_per_mtok: Decimal | None = None, price_out_per_mtok: Decimal | None = None) -> dict:
    n = len(results)
    routed = {"pass": 0, "manual_review": 0, "reject": 0}
    for r in results:
        routed[r["route"]] += 1
    auto_pass = [r for r in results if r["route"] == "pass"]
    auto_reject = [r for r in results if r["route"] == "reject"]
    decided = auto_pass + auto_reject
    violations = sum(1 for r in results if r["format_violation"])
    tokens_in = [r["input_tokens"] for r in results if r["input_tokens"] is not None]
    tokens_out = [r["output_tokens"] for r in results if r["output_tokens"] is not None]
    cost = None
    if price_in_per_mtok is not None and price_out_per_mtok is not None and n and len(tokens_in) == n and len(tokens_out) == n:
        total = (Decimal(sum(tokens_in)) * price_in_per_mtok + Decimal(sum(tokens_out)) * price_out_per_mtok) / Decimal(1_000_000)
        cost = (total / n).quantize(Decimal("0.000001"))
    loop = _loop_metrics(results) if results and "steps" in results[0] else None
    truth_counts = {"pass": sum(1 for r in results if r["truth"] == "pass"), "reject": sum(1 for r in results if r["truth"] == "reject")}
    return {
        "items": n,
        "truth_counts": truth_counts,
        "routed_counts": routed,
        "auto_pass_error_rate": _ratio(sum(1 for r in auto_pass if r["truth"] == "reject"), len(auto_pass)),
        "auto_reject_error_rate": _ratio(sum(1 for r in auto_reject if r["truth"] == "pass"), len(auto_reject)),
        "manual_review_rate": _ratio(routed["manual_review"], n),
        "decided_agreement": _ratio(sum(1 for r in decided if r["truth"] == r["route"]), len(decided)),
        "cohen_kappa_decided": cohen_kappa([(r["truth"], r["route"]) for r in decided]),
        "format_violation_rate": _ratio(violations, n),
        # H1 is single-call (None); H2 = corrected / (corrected + downgraded).
        "validator_correction_success_rate": None if loop is None else loop["correction_success_rate"],
        "tokens": {"input_total": sum(tokens_in), "output_total": sum(tokens_out), "items_with_usage": len(tokens_in)},
        "cost_per_item_usd": cost,
        "latency_ms": {"p50": percentile([r["latency_ms"] for r in results], 50), "p95": percentile([r["latency_ms"] for r in results], 95)},
        **({} if loop is None else {"loop": loop}),
    }


def _loop_metrics(results: list[dict]) -> dict:
    """ADR-019 loop metrics. Rates are over items whose first answer parsed (format violations never loop).
    Correction success = items with correctable errors on the first answer that ended valid, corrected or
    flagged (i.e. their correctable errors were fixed without changing the judgement)."""
    from collections import Counter

    from labelhub_evals.review_validation import CORRECTABLE

    looped = [r for r in results if r["loop_outcome"] != "format_violation"]
    outcomes = Counter(r["loop_outcome"] for r in results)
    initial = Counter(code for r in looped for code in r["initial_error_codes"])
    final = Counter(code for r in looped for code in r["final_error_codes"])
    needs_fix = [r for r in looped if CORRECTABLE & set(r["initial_error_codes"])]
    fixed = [r for r in needs_fix if r["loop_outcome"] in ("corrected", "flagged")]
    return {
        "outcomes": {k: outcomes.get(k, 0) for k in ("valid", "corrected", "flagged", "downgraded", "judgement_changed", "correction_failed", "format_violation")},
        "initial_invalid_rate": _ratio(sum(1 for r in looped if r["initial_error_codes"]), len(looped)),
        "initial_correctable_rate": _ratio(len(needs_fix), len(looped)),
        "flagged_rate": _ratio(outcomes.get("flagged", 0), len(looped)),
        "final_invalid_rate": _ratio(sum(outcomes.get(k, 0) for k in ("downgraded", "judgement_changed", "correction_failed")), len(looped)),
        "correction_success_rate": _ratio(len(fixed), len(needs_fix)),
        "mean_steps": None if not results else (Decimal(sum(r["steps"] for r in results)) / Decimal(len(results))).quantize(Decimal("0.0001")),
        "model_calls": sum(r["steps"] for r in results),
        "initial_error_codes": dict(sorted(initial.items())),
        "final_error_codes": dict(sorted(final.items())),
    }
