"""Metrics for three-zone AI pre-review against human ground truth.

truth: accept | borderline | reject      route: pass | manual_review | reject
The primary risk metric is the auto-pass error rate: of everything the AI would let through without a human,
how much should not have been accepted. Rates carry Wilson 95% intervals because the sets are small.
"""

from __future__ import annotations

import math
from collections import Counter
from decimal import Decimal
from typing import Any

TRUTHS = ("accept", "borderline", "reject")
ROUTES = ("pass", "manual_review", "reject")


def wilson(successes: int, total: int, z: float = 1.959964) -> tuple[float, float] | None:
    if total == 0:
        return None
    p = successes / total
    denom = 1 + z * z / total
    centre = (p + z * z / (2 * total)) / denom
    half = z * math.sqrt(p * (1 - p) / total + z * z / (4 * total * total)) / denom
    return (max(0.0, centre - half), min(1.0, centre + half))


def rate(successes: int, total: int) -> dict[str, Any]:
    return {"value": (successes / total) if total else None, "numerator": successes, "denominator": total,
            "ci95": wilson(successes, total)}


def cohen_kappa(a: list[bool], b: list[bool]) -> float | None:
    """Agreement between two binary raters beyond chance."""
    n = len(a)
    if n == 0 or n != len(b):
        return None
    observed = sum(x == y for x, y in zip(a, b, strict=True)) / n
    pa, pb = sum(a) / n, sum(b) / n
    expected = pa * pb + (1 - pa) * (1 - pb)
    if expected == 1:
        return None
    return (observed - expected) / (1 - expected)


def auroc(positive_scores: list[float], negative_scores: list[float]) -> float | None:
    """Probability a random accept case scores above a random reject case (Mann-Whitney U, ties count half).
    Threshold-free: it says how separable the scores are before any calibration (H3)."""
    if not positive_scores or not negative_scores:
        return None
    wins = 0.0
    for p in positive_scores:
        for q in negative_scores:
            wins += 1.0 if p > q else 0.5 if p == q else 0.0
    return wins / (len(positive_scores) * len(negative_scores))


def percentile(values: list[float], q: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    index = (len(ordered) - 1) * q
    low, high = math.floor(index), math.ceil(index)
    return ordered[low] + (ordered[high] - ordered[low]) * (index - low)


def summarize(results: list[dict[str, Any]]) -> dict[str, Any]:
    n = len(results)
    confusion = {t: {r: 0 for r in ROUTES} for t in TRUTHS}
    for row in results:
        confusion[row["truth"]][row["route"]] += 1

    passed = [r for r in results if r["route"] == "pass"]
    rejected = [r for r in results if r["route"] == "reject"]
    manual = [r for r in results if r["route"] == "manual_review"]
    decided_clear = [r for r in results if r["route"] != "manual_review" and r["truth"] != "borderline"]
    scored = [r for r in results if r["final_score"] is not None]
    errors = Counter(r["error"] for r in results if r["error"])
    warnings = Counter(w.split(":")[0] for r in results for w in r.get("warnings", []))
    latencies = [r["latency_ms"] for r in results if r.get("latency_ms") is not None]
    cost = sum((Decimal(r["cost_usd"]) for r in results if r.get("cost_usd") is not None), Decimal(0))

    return {
        "cases": n,
        "truthCounts": dict(Counter(r["truth"] for r in results)),
        "routeCounts": dict(Counter(r["route"] for r in results)),
        "confusion": confusion,
        # risk: auto-passed but a careful human would not have accepted it
        "autoPassErrorRate": rate(sum(r["truth"] != "accept" for r in passed), len(passed)),
        # waste: auto-rejected although it was acceptable
        "autoRejectErrorRate": rate(sum(r["truth"] == "accept" for r in rejected), len(rejected)),
        # human workload left after AI triage
        "humanReviewRate": rate(len(manual), n),
        # clear-cut cases the AI decided on its own, how often it was right
        "decidedAccuracy": rate(
            sum((r["route"] == "pass") == (r["truth"] == "accept") for r in decided_clear), len(decided_clear)),
        "kappaPassVsAccept": cohen_kappa([r["route"] == "pass" for r in results], [r["truth"] == "accept" for r in results]),
        "aurocAcceptVsReject": auroc(
            [float(r["final_score"]) for r in scored if r["truth"] == "accept"],
            [float(r["final_score"]) for r in scored if r["truth"] == "reject"]),
        "formatViolationRate": rate(sum(errors.values()), n),
        "errorCodes": dict(errors),
        "warningCodes": dict(warnings),
        "latencyMs": {"p50": percentile(latencies, 0.5), "p95": percentile(latencies, 0.95)},
        "costUsd": {"total": str(cost), "perCase": str((cost / n).quantize(Decimal("0.000001"))) if n else None,
                    "per1kCases": str((cost * 1000 / n).quantize(Decimal("0.0001"))) if n else None},
    }
