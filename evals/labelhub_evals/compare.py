"""Compare a candidate run with a baseline run (ADR-017 §1.5 evaluation gate).

Both runs must use the same golden set (same sha256); otherwise the comparison is meaningless and
refused. The gate is the one ADR-019 sets for H2: the auto-pass error rate must not rise. Every other
metric is reported next to it, favourable or not.
"""

from __future__ import annotations

import json
from decimal import Decimal
from pathlib import Path

ROWS = (
    ("auto_pass_error_rate", "Auto-pass error rate (gate: must not rise)", "pct", "lower"),
    ("auto_reject_error_rate", "Auto-reject error rate", "pct", "lower"),
    ("manual_review_rate", "Manual-review rate", "pct", "lower"),
    ("decided_agreement", "Agreement on decided items", "pct", "higher"),
    ("cohen_kappa_decided", "Cohen's κ on decided items", "num", "higher"),
    ("format_violation_rate", "Format violation rate", "pct", "lower"),
)


class CompareError(ValueError):
    pass


def _load(run_dir: Path) -> dict:
    return json.loads((run_dir / "scorecard.json").read_text(encoding="utf-8"))


def _fmt(value, kind: str) -> str:
    if value is None:
        return "n/a"
    d = Decimal(str(value))
    return f"{d * 100:.2f}%" if kind == "pct" else f"{d:.4f}"


def _delta(base, cand, kind: str) -> str:
    if base is None or cand is None:
        return "n/a"
    d = Decimal(str(cand)) - Decimal(str(base))
    return (f"{d * 100:+.2f} pp" if kind == "pct" else f"{d:+.4f}")


def compare(base_dir: Path, cand_dir: Path) -> tuple[str, bool]:
    base, cand = _load(base_dir), _load(cand_dir)
    if base["run"]["golden"]["sha256"] != cand["run"]["golden"]["sha256"]:
        raise CompareError("runs use different golden sets; comparison refused")
    b, c = base["scorecard"], cand["scorecard"]
    gate_base, gate_cand = b["auto_pass_error_rate"], c["auto_pass_error_rate"]
    if gate_cand is None:
        passed = True  # nothing auto-passed, so no auto-pass error can have risen
    elif gate_base is None:
        passed = Decimal(str(gate_cand)) == 0
    else:
        passed = Decimal(str(gate_cand)) <= Decimal(str(gate_base))
    lines = [
        f"# Eval comparison: `{cand['run_id']}` vs baseline `{base['run_id']}`",
        "",
        f"- Baseline: `{base['run']['model']}` · prompt `{base['run']['prompt_version']}`" + (" · loop" if "loop" in base["run"] else ""),
        f"- Candidate: `{cand['run']['model']}` · prompt `{cand['run']['prompt_version']}`"
        + (f" · loop `{cand['run']['loop']['feedback_version']}` max {cand['run']['loop']['max_steps']}" if "loop" in cand["run"] else ""),
        f"- Golden set: `{cand['run']['golden']['name']}` (`{cand['run']['golden']['sha256'][:12]}`), {c['items']} items",
        f"- **Gate (auto-pass error rate must not rise): {'PASS' if passed else 'FAIL'}**",
        "",
        "| Metric | Baseline | Candidate | Δ |",
        "|---|---|---|---|",
    ]
    for key, label, kind, _ in ROWS:
        lines.append(f"| {label} | {_fmt(b[key], kind)} | {_fmt(c[key], kind)} | {_delta(b[key], c[key], kind)} |")
    rb, rc = b["routed_counts"], c["routed_counts"]
    lines.append(f"| Routed pass / manual / reject | {rb['pass']} / {rb['manual_review']} / {rb['reject']} | {rc['pass']} / {rc['manual_review']} / {rc['reject']} | |")
    lines.append(f"| Tokens in / out | {b['tokens']['input_total']} / {b['tokens']['output_total']} | {c['tokens']['input_total']} / {c['tokens']['output_total']} | |")
    lines.append(f"| Latency p50 / p95 (ms) | {b['latency_ms']['p50']} / {b['latency_ms']['p95']} | {c['latency_ms']['p50']} / {c['latency_ms']['p95']} | |")
    if "loop" in c:
        loop = c["loop"]
        lines.append(f"| Invalid first answer → after loop | | {_fmt(loop['initial_invalid_rate'], 'pct')} → {_fmt(loop['final_invalid_rate'], 'pct')} | |")
        lines.append(f"| Correction success rate | | {_fmt(loop['correction_success_rate'], 'pct')} | |")
        lines.append(f"| Model calls | {b['items']} | {loop['model_calls']} | |")
    return "\n".join(lines) + "\n", passed
