import pytest

from labelhub_evals import metrics


def test_cohen_kappa_textbook_value():
    rater_a = [True] * 25 + [False] * 25
    rater_b = [True] * 20 + [False] * 5 + [True] * 10 + [False] * 15  # po = 0.7, pe = 0.5

    assert metrics.cohen_kappa(rater_a, rater_b) == pytest.approx(0.4)


def test_auroc_is_one_for_perfect_separation_and_half_for_ties():
    assert metrics.auroc([0.9, 0.8], [0.1, 0.2]) == 1.0
    assert metrics.auroc([0.5], [0.5]) == 0.5
    assert metrics.auroc([], [0.1]) is None


def test_wilson_interval_known_value():
    low, high = metrics.wilson(5, 10)

    assert (round(low, 4), round(high, 4)) == (0.2366, 0.7634)


def _row(truth, route, score="0.5"):
    return {"truth": truth, "route": route, "final_score": score, "error": None, "warnings": [],
            "latency_ms": 100, "cost_usd": "0.0001"}


def test_auto_pass_error_rate_counts_non_accept_cases_that_were_auto_passed():
    rows = [_row("accept", "pass"), _row("borderline", "pass"), _row("reject", "pass"),
            _row("accept", "reject"), _row("reject", "manual_review")]

    summary = metrics.summarize(rows)

    assert summary["autoPassErrorRate"]["numerator"] == 2
    assert summary["autoPassErrorRate"]["denominator"] == 3
    assert summary["autoRejectErrorRate"]["value"] == 1.0
    assert summary["humanReviewRate"]["value"] == pytest.approx(0.2)
    assert summary["confusion"]["reject"]["pass"] == 1
