"""Markdown report for one run: headline risk metrics first, then the evidence behind them."""

from __future__ import annotations

from typing import Any

from labelhub_evals.metrics import ROUTES, TRUTHS


def _pct(metric: dict[str, Any]) -> str:
    if metric["value"] is None:
        return "n/a"
    low, high = metric["ci95"]
    return f"{metric['value']:.1%} ({metric['numerator']}/{metric['denominator']}, 95% CI {low:.1%}–{high:.1%})"


def _num(value: float | None, fmt: str = "{:.3f}") -> str:
    return "n/a" if value is None else fmt.format(value)


def render(manifest: dict[str, Any], summary: dict[str, Any]) -> str:
    rule = manifest["rule"]
    lines = [
        f"# Eval report: {manifest['system']} · {manifest['model']} · {manifest['dataset']['name']}",
        "",
        f"- Cases: {summary['cases']} ({manifest['dataset']['source']}, {manifest['dataset']['labelMapping']})",
        f"- Rule: prompt `{rule['prompt']}`, dimensions {rule['dimensions']}, "
        f"pass ≥ {rule['passThreshold']}, reject ≤ {rule['rejectThreshold']} ({rule['scoringRuleVersion']})",
        f"- Provider: `{manifest['provider']}` · git `{(manifest['git']['commit'] or 'unknown')[:12]}`"
        + (" (dirty)" if manifest["git"]["dirty"] else ""),
        "",
        "## Headline",
        "",
        "| Metric | Value |",
        "|---|---|",
        f"| **Auto-pass error rate** (passed without a human, but not acceptable) | {_pct(summary['autoPassErrorRate'])} |",
        f"| Auto-reject error rate (rejected, but acceptable) | {_pct(summary['autoRejectErrorRate'])} |",
        f"| Human review rate (workload left after triage) | {_pct(summary['humanReviewRate'])} |",
        f"| Accuracy on clear-cut cases the AI decided | {_pct(summary['decidedAccuracy'])} |",
        f"| Cohen's κ (pass vs accept) | {_num(summary['kappaPassVsAccept'])} |",
        f"| AUROC of final score (accept vs reject) | {_num(summary['aurocAcceptVsReject'])} |",
        f"| Format violation rate | {_pct(summary['formatViolationRate'])} |",
        f"| Latency p50 / p95 | {_num(summary['latencyMs']['p50'], '{:.0f}')} / {_num(summary['latencyMs']['p95'], '{:.0f}')} ms |",
        f"| Cost | ${summary['costUsd']['total']} total · ${summary['costUsd']['per1kCases']} per 1k cases |",
        "",
        "## Confusion (rows: human truth, columns: AI route)",
        "",
        "| truth \\ route | " + " | ".join(ROUTES) + " |",
        "|---|" + "---|" * len(ROUTES),
    ]
    for truth in TRUTHS:
        lines.append(f"| {truth} | " + " | ".join(str(summary["confusion"][truth][route]) for route in ROUTES) + " |")
    lines += ["", "## Failures and fallbacks", ""]
    lines.append(f"- Hard errors (routed to a human): {summary['errorCodes'] or 'none'}")
    lines.append(f"- Soft warnings: {summary['warningCodes'] or 'none'}")
    lines += [
        "",
        "## Reproduce",
        "",
        "```bash",
        f"python -m labelhub_evals run --dataset {manifest['dataset']['name']} --model {manifest['model']} "
        f"--prompt {rule['prompt']} --provider replay",
        "```",
        "",
    ]
    return "\n".join(lines)
