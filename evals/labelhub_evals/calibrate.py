"""ADR-020 §2 threshold calibration on a recorded run (no model calls).

LabelHub semantics this models (suggest mode, as implemented):
- every submission is reviewed by a reviewer; a reviewer reject is final (never exported);
- after a reviewer approve, a senior arbitration case opens if any escalation signal fired:
    manual zone   pass_threshold > score > reject_threshold        (ai_manual_review)
    loop flag     agent loop outcome flagged/downgraded/...          (ai_loop_flagged, toggle)
    disagreement  score <= disagreement_threshold, reviewer approved (ai_reviewer_disagreement)
- seniors are assumed correct; reviewers err symmetrically with probability e (an assumption,
  reported next to every number).

Per configuration it reports, in expectation over reviewer errors:
- escalation rate  = senior cases opened / submissions (senior workload)
- bad exports      = truth-reject submissions a reviewer approved and no signal escalated, per 1000
"""

from __future__ import annotations

import itertools
import json
from dataclasses import dataclass
from decimal import Decimal
from pathlib import Path

LOOP_ESCALATION_OUTCOMES = {"flagged", "downgraded", "judgement_changed", "correction_failed", "downgraded_no_correction_support"}
GRID = tuple(Decimal(x) / 100 for x in range(0, 100, 5))


@dataclass(frozen=True)
class Policy:
    pass_threshold: Decimal
    reject_threshold: Decimal
    disagreement_threshold: Decimal | None  # None = disagreement escalation off
    loop_flag: bool

    def label(self) -> str:
        d = "off" if self.disagreement_threshold is None else f"≤ {self.disagreement_threshold:.2f}"
        return f"pass ≥ {self.pass_threshold:.2f} · reject ≤ {self.reject_threshold:.2f} · disagreement {d} · loop flag {'on' if self.loop_flag else 'off'}"


@dataclass(frozen=True)
class Point:
    policy: Policy
    escalation_rate: Decimal
    bad_per_1000: Decimal


SCALE = 10000  # scores carry 4 decimals; integer compare is exact


def _bp(value: Decimal | None) -> int | None:
    return None if value is None else int(value * SCALE)


def _escalates(score_bp: int, loop_flagged: bool, policy: Policy) -> bool:
    if _bp(policy.reject_threshold) < score_bp < _bp(policy.pass_threshold):
        return True
    if policy.loop_flag and loop_flagged:
        return True
    return policy.disagreement_threshold is not None and score_bp <= _bp(policy.disagreement_threshold)


def counts(rows: list[dict], policy: Policy) -> tuple[int, int, int]:
    """(truth-pass escalated, truth-reject escalated, truth-reject not escalated). Expected escalations and bad
    exports are linear in the reviewer error e, so these counts are computed once and reused for every e."""
    pass_esc = reject_esc = reject_missed = 0
    for row in rows:
        escalated = _escalates(row["_bp"], row["_flag"], policy)
        if row["truth"] == "pass":
            pass_esc += escalated
        elif escalated:
            reject_esc += 1
        else:
            reject_missed += 1
    return pass_esc, reject_esc, reject_missed


def point(n: int, policy: Policy, c: tuple[int, int, int], reviewer_error: Decimal) -> Point:
    pass_esc, reject_esc, reject_missed = c
    escalations = (1 - reviewer_error) * pass_esc + reviewer_error * reject_esc
    bad = reviewer_error * reject_missed
    return Point(policy, (escalations / n).quantize(Decimal("0.0001")), (bad / n * 1000).quantize(Decimal("0.01")))


def prepare(rows: list[dict]) -> list[dict]:
    return [dict(r, _bp=int(Decimal(r["score"]) * SCALE), _flag=r.get("loop_outcome") in LOOP_ESCALATION_OUTCOMES) for r in rows]


def evaluate(rows: list[dict], policy: Policy, reviewer_error: Decimal) -> Point:
    prepared = rows if rows and "_bp" in rows[0] else prepare(rows)
    return point(len(prepared), policy, counts(prepared, policy), reviewer_error)


def policies(with_loop: bool):
    for p, r in itertools.product(GRID, GRID):
        if r >= p or p < Decimal("0.50"):
            continue
        for d in (None,) + tuple(g for g in GRID if g <= r):
            for flag in ((True, False) if with_loop else (False,)):
                yield Policy(p, r, d, flag)


def frontier(points: list[Point]) -> list[Point]:
    """Pareto frontier: lowest bad exports for each escalation budget (both minimized)."""
    best: list[Point] = []
    for pt in sorted(points, key=lambda x: (x.escalation_rate, x.bad_per_1000, x.policy.label())):
        if not best or pt.bad_per_1000 < best[-1].bad_per_1000:
            best.append(pt)
    return best


def load(run_dir: Path) -> list[dict]:
    rows = [json.loads(line) for line in (run_dir / "results.jsonl").read_text(encoding="utf-8").splitlines() if line.strip()]
    return prepare([r for r in rows if r["score"] is not None])


CURRENT_SUGGEST = Policy(Decimal("0.80"), Decimal("0.20"), None, False)


def folds(rows: list[dict]) -> tuple[list[dict], list[dict]]:
    """Deterministic 2-fold split by item_id (a content hash), so the held-out check is reproducible."""
    a = [r for r in rows if int(r["item_id"][:8], 16) % 2 == 0]
    b = [r for r in rows if int(r["item_id"][:8], 16) % 2 == 1]
    return a, b


def best_under(rows: list[dict], all_policies: list[Policy], e: Decimal, budget: Decimal, cache: dict | None = None) -> Point | None:
    n = len(rows)
    points = []
    for pol in all_policies:
        key = (id(rows), pol)
        c = cache.get(key) if cache is not None else None
        if c is None:
            c = counts(rows, pol)
            if cache is not None:
                cache[key] = c
        points.append(point(n, pol, c, e))
    feasible = [p for p in points if p.escalation_rate <= budget]
    return min(feasible, key=lambda p: (p.bad_per_1000, p.escalation_rate, p.policy.label())) if feasible else None


def render(run_id: str, rows: list[dict], reviewer_errors: list[Decimal], budgets: list[Decimal]) -> str:
    with_loop = any("loop_outcome" in r for r in rows)
    all_policies = list(policies(with_loop))
    lines = [
        f"# Threshold calibration for run `{run_id}`",
        "",
        "Generated by `python -m labelhub_evals calibrate`; do not edit by hand. No model calls: every number replays recorded answers.",
        "",
        f"- Items: {len(rows)} · searched {len(all_policies)} policies (pass/reject/disagreement thresholds on a 0.05 grid"
        + (", loop-flag escalation on/off)" if with_loop else ")"),
        "- **Escalation rate**: share of submissions that open a senior arbitration case. **Bad exports**: truth-reject submissions"
        " a reviewer approved and no signal escalated, per 1000 submissions.",
        "- **Assumptions**: reviewers err symmetrically with probability *e*; seniors are correct; a case opens only after a reviewer"
        " approve (as `SeniorReviewCaseService` does). *e* is not measured here — compare rows across *e*.",
        "",
    ]
    rows = rows if rows and "_bp" in rows[0] else prepare(rows)
    full_counts = {pol: counts(rows, pol) for pol in all_policies}
    fold_a, fold_b = folds(rows)
    cache: dict = {}
    for e in reviewer_errors:
        points = [point(len(rows), pol, full_counts[pol], e) for pol in all_policies]
        cur = evaluate(rows, CURRENT_SUGGEST, e)
        cur_d = evaluate(rows, Policy(Decimal("0.80"), Decimal("0.20"), Decimal("0.20"), with_loop), e)
        lines += [
            f"## Reviewer error e = {e:.2f}",
            "",
            "| Policy | Escalation rate | Bad exports / 1000 |",
            "|---|---|---|",
            f"| Current (before W3): {CURRENT_SUGGEST.label()} | {cur.escalation_rate * 100:.1f}% | {cur.bad_per_1000} |",
            f"| W3a defaults if every new signal were on: {cur_d.policy.label()} | {cur_d.escalation_rate * 100:.1f}% | {cur_d.bad_per_1000} |",
            "",
            "Best policy per escalation budget. *In-sample* picks and scores on all items (optimistic); *held-out* picks on one half"
            " and scores on the other (2-fold by item_id, averaged) — trust the held-out columns.",
            "",
            "| Budget | Policy (in-sample pick) | Escalation in-sample | Bad / 1000 in-sample | Escalation held-out | Bad / 1000 held-out |",
            "|---|---|---|---|---|---|",
        ]
        for budget in budgets:
            best = min((p for p in points if p.escalation_rate <= budget),
                       key=lambda p: (p.bad_per_1000, p.escalation_rate, p.policy.label()), default=None)
            if best is None:
                lines.append(f"| ≤ {budget * 100:.0f}% | infeasible | | | | |")
                continue
            held = []
            for fit, test in ((fold_a, fold_b), (fold_b, fold_a)):
                pick = best_under(fit, all_policies, e, budget, cache)
                if pick is not None:
                    held.append(evaluate(test, pick.policy, e))
            if len(held) == 2:
                h_esc = (sum(h.escalation_rate for h in held) / 2 * 100).quantize(Decimal("0.1"))
                h_bad = (sum(h.bad_per_1000 for h in held) / 2).quantize(Decimal("0.01"))
                held_cells = f"{h_esc}% | {h_bad}"
            else:
                held_cells = "n/a | n/a"
            lines.append(f"| ≤ {budget * 100:.0f}% | {best.policy.label()} | {best.escalation_rate * 100:.1f}% | {best.bad_per_1000} | {held_cells} |")
        lines += ["", "<details><summary>Full Pareto frontier</summary>", "", "| Escalation rate | Bad exports / 1000 | Policy |", "|---|---|---|"]
        lines += [f"| {p.escalation_rate * 100:.1f}% | {p.bad_per_1000} | {p.policy.label()} |" for p in frontier(points)]
        lines += ["", "</details>", ""]
    return "\n".join(lines)
