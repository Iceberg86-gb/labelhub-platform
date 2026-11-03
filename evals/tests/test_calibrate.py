"""ADR-020 §2 calibration contracts: the escalation model mirrors SeniorReviewCaseService, the frontier is
Pareto-optimal, and the committed report regenerates byte for byte."""

from __future__ import annotations

from decimal import Decimal as D
from pathlib import Path

from labelhub_evals.calibrate import Policy, evaluate, folds, frontier, load, render

EVALS = Path(__file__).resolve().parents[1]


def row(truth, score, outcome="valid", item="00000000"):
    return {"truth": truth, "score": score, "loop_outcome": outcome, "item_id": item}


def test_escalation_signals_match_the_api_rules():
    p = Policy(D("0.80"), D("0.20"), D("0.10"), False)
    e = D("0")  # perfect reviewer: approves exactly the truth-pass items
    assert evaluate([row("pass", "0.50")], p, e).escalation_rate == 1            # manual zone
    assert evaluate([row("pass", "0.90")], p, e).escalation_rate == 0            # pass zone, no signal
    assert evaluate([row("pass", "0.05")], p, e).escalation_rate == 1            # score <= disagreement threshold
    assert evaluate([row("pass", "0.15")], p, e).escalation_rate == 0            # reject zone above disagreement threshold
    assert evaluate([row("pass", "0.90", "flagged")], p, e).escalation_rate == 0  # loop flag off
    on = Policy(D("0.80"), D("0.20"), None, True)
    assert evaluate([row("pass", "0.90", "flagged")], on, e).escalation_rate == 1


def test_cases_open_only_after_a_reviewer_approve_and_bad_exports_need_a_reviewer_mistake():
    p = Policy(D("0.80"), D("0.20"), None, False)
    e = D("0.10")
    truth_reject_in_pass_zone = [row("reject", "0.95")]
    point = evaluate(truth_reject_in_pass_zone, p, e)
    assert point.escalation_rate == 0
    assert point.bad_per_1000 == D("100.00")  # exported only when the reviewer wrongly approves (10%)
    truth_reject_in_manual_zone = [row("reject", "0.50")]
    point = evaluate(truth_reject_in_manual_zone, p, e)
    assert point.escalation_rate == D("0.1000") and point.bad_per_1000 == 0  # escalated after the mistaken approve


def test_frontier_is_pareto_optimal():
    from labelhub_evals.calibrate import Point

    pts = [Point(Policy(D("0.8"), D("0.2"), None, False), D(esc), D(bad)) for esc, bad in
           [("0.10", "20"), ("0.20", "10"), ("0.20", "15"), ("0.30", "12"), ("0.40", "5")]]
    assert [(str(p.escalation_rate), str(p.bad_per_1000)) for p in frontier(pts)] == [("0.10", "20"), ("0.20", "10"), ("0.40", "5")]


def test_folds_partition_deterministically():
    rows = [row("pass", "0.5", item=f"{i:08x}") for i in range(10)]
    a, b = folds(rows)
    assert len(a) + len(b) == 10 and not {r["item_id"] for r in a} & {r["item_id"] for r in b}
    assert folds(rows) == (a, b)


def test_committed_calibration_report_regenerates_byte_for_byte():
    run = EVALS / "runs" / "76b0cd706ca35fdd"
    text = render(run.name, load(run), [D("0.05"), D("0.10"), D("0.20")], [D("0.15"), D("0.25"), D("0.35"), D("0.45")])
    assert (EVALS / "reports" / "w3a-calibration.md").read_text(encoding="utf-8") == text
