"""Run one evaluation and persist it as an append-only fact (ADR-017 §3).

run_id is derived from everything that determines the outcome (golden set hash, model identity,
prompt version, scoring rule and thresholds), so the same configuration always maps to the same
directory. Writing into an existing run directory is only allowed when the bytes are identical:
that is exactly the H1 gate "the baseline scorecard reproduces".
"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from decimal import Decimal
from pathlib import Path

from labelhub_evals import metrics, review
from labelhub_evals.canonical import canonical_hash, canonical_json
from labelhub_evals.golden import GoldenItem, jsonl_bytes
from labelhub_evals.providers.base import ProviderResponse
from labelhub_evals.recording import RecordingProvider


class RunConflict(RuntimeError):
    pass


@dataclass(frozen=True)
class RunConfig:
    golden_name: str
    model: str
    prompt_version: str = review.PROMPT_VERSION
    scoring_rule_version: str = review.SCORING_RULE_VERSION
    pass_threshold: Decimal = review.DEFAULT_PASS_THRESHOLD
    reject_threshold: Decimal = review.DEFAULT_REJECT_THRESHOLD
    dimensions: tuple[str, ...] = review.DEFAULT_DIMENSIONS
    # ADR-019 (H2). None = H1 single call; identity (and run_id) of H1 runs is unchanged.
    loop_max_steps: int | None = None
    correction_style: str = "tool"
    # ADR-021 (H4). None = no precedents; identity (and run_id) of H1-H3 runs is unchanged.
    precedents_k: int | None = None
    precedent_retrieval_sha256: str | None = None

    def identity(self, golden_sha256: str) -> dict:
        ident = self._base_identity(golden_sha256)
        if self.loop_max_steps is not None:
            from labelhub_evals.review_loop import FEEDBACK_VERSION
            from labelhub_evals.review_validation import RULES_VERSION

            ident["loop"] = {"correction_style": self.correction_style, "feedback_version": FEEDBACK_VERSION,
                             "max_steps": self.loop_max_steps, "rules_version": RULES_VERSION}
        if self.precedents_k is not None:
            from labelhub_evals.precedents import PRECEDENT_VERSION

            ident["precedents"] = {"k": self.precedents_k, "retrieval_sha256": self.precedent_retrieval_sha256, "version": PRECEDENT_VERSION}
        return ident

    def _base_identity(self, golden_sha256: str) -> dict:
        return {
            "dimensions": list(self.dimensions),
            "golden": {"name": self.golden_name, "sha256": golden_sha256},
            "model": self.model,
            "pass_threshold": str(self.pass_threshold),
            "prompt_version": self.prompt_version,
            "reject_threshold": str(self.reject_threshold),
            "scoring_rule_version": self.scoring_rule_version,
        }


def evaluate_item(item: GoldenItem, response: ProviderResponse, config: RunConfig) -> dict:
    violation = response.violation
    output = None
    if violation is None:
        try:
            output = review.parse_tool_arguments(response.arguments or "")
        except review.FormatViolation as exc:
            violation = str(exc)
    if output is None:
        # Deterministic downgrade (ADR-006/ADR-017): an unusable answer is routed to a human.
        route, score = "manual_review", None
    else:
        routing = review.route(output, dimensions=config.dimensions, pass_threshold=config.pass_threshold, reject_threshold=config.reject_threshold)
        route, score = routing.route, str(routing.score)
    return {
        "item_id": item.item_id,
        "truth": item.truth,
        "route": route,
        "score": score,
        "model_suggestion": None if output is None else output["overallSuggestion"],
        "format_violation": violation is not None,
        "violation": violation,
        "input_tokens": response.input_tokens,
        "output_tokens": response.output_tokens,
        "latency_ms": response.latency_ms,
    }


def evaluate_loop_item(item: GoldenItem, recorder, config: RunConfig, precedents=None) -> dict:
    """H2: run produce -> validate -> correct, then score the final output exactly like H1 (routing by
    score, as the API does). The review input's keys are the fields a finding may reference."""
    from labelhub_evals.review_loop import downgraded_output, run_loop
    from labelhub_evals.review_validation import ValidationContext

    ctx = ValidationContext(config.dimensions, config.pass_threshold, config.reject_threshold, frozenset(item.input))
    loop = run_loop(recorder, item.item_id, item.input, ctx, config.loop_max_steps, config.correction_style, precedents)
    final = loop.final
    if loop.downgrades:
        final = ProviderResponse(json.dumps(downgraded_output(json.loads(final.arguments), loop.final_errors), sort_keys=True),
                                 None, final.input_tokens, final.output_tokens, final.latency_ms)

    def total(attr):
        values = [getattr(r, attr) for r in loop.responses if getattr(r, attr) is not None]
        return sum(values) if values else None

    final = ProviderResponse(final.arguments, final.violation, total("input_tokens"), total("output_tokens"), sum(r.latency_ms for r in loop.responses))
    row = evaluate_item(item, final, config)
    codes = lambda errors: sorted({e.code for e in errors})  # noqa: E731
    row.update({
        "steps": loop.steps,
        "loop_outcome": loop.outcome,
        "initial_error_codes": codes(loop.step_errors[0]) if loop.step_errors else [],
        "final_error_codes": codes(loop.final_errors),
    })
    return row


def _json_default(value):
    if isinstance(value, Decimal):
        return str(value)
    raise TypeError(type(value))


def render_markdown(card: dict) -> str:
    s = card["scorecard"]
    ident = card["run"]
    pct = lambda v: "n/a" if v is None else f"{Decimal(v) * 100:.2f}%"  # noqa: E731
    lines = [
        f"# Eval run `{card['run_id']}`",
        "",
        f"- Golden set: `{ident['golden']['name']}` (`{ident['golden']['sha256'][:12]}`), {s['items']} items, truth pass/reject = {s['truth_counts']['pass']}/{s['truth_counts']['reject']}",
        f"- Model: `{ident['model']}`  ·  prompt `{ident['prompt_version']}`  ·  scoring `{ident['scoring_rule_version']}` (pass ≥ {ident['pass_threshold']}, reject ≤ {ident['reject_threshold']})",
        "",
        "| Metric | Value |",
        "|---|---|",
        f"| **Auto-pass error rate** (primary risk) | {pct(s['auto_pass_error_rate'])} |",
        f"| Auto-reject error rate | {pct(s['auto_reject_error_rate'])} |",
        f"| Manual-review rate | {pct(s['manual_review_rate'])} |",
        f"| Agreement on decided items | {pct(s['decided_agreement'])} |",
        f"| Cohen's κ on decided items | {'n/a' if s['cohen_kappa_decided'] is None else s['cohen_kappa_decided']} |",
        f"| Format violation rate | {pct(s['format_violation_rate'])} |",
        f"| Routed pass / manual / reject | {s['routed_counts']['pass']} / {s['routed_counts']['manual_review']} / {s['routed_counts']['reject']} |",
        f"| Tokens in / out (total) | {s['tokens']['input_total']} / {s['tokens']['output_total']} |",
        f"| Cost per item (USD) | {s['cost_per_item_usd'] or 'n/a'} |",
        f"| Latency p50 / p95 (ms) | {s['latency_ms']['p50']} / {s['latency_ms']['p95']} |",
        "",
    ]
    if "precedents" in ident:
        pc = ident["precedents"]
        lines[3] += f"  ·  precedents `{pc['version']}` top {pc['k']} (retrieval `{pc['retrieval_sha256'][:12]}`)"
    if "loop" in s:
        loop, cfg = s["loop"], ident["loop"]
        o = loop["outcomes"]
        lines += [
            f"## Validate-and-correct loop (`{cfg['feedback_version']}`, max {cfg['max_steps']} steps, {cfg['correction_style']} style, rules `{cfg['rules_version']}`)",
            "",
            "| Metric | Value |",
            "|---|---|",
            f"| Invalid on first answer (any rule / correctable) | {pct(loop['initial_invalid_rate'])} / {pct(loop['initial_correctable_rate'])} |",
            f"| Flagged (judgement inconsistency, manual_review, not sent to the model) | {pct(loop['flagged_rate'])} |",
            f"| Downgraded after the loop (still invalid, judgement changed or correction failed) | {pct(loop['final_invalid_rate'])} |",
            f"| Correction success rate | {pct(loop['correction_success_rate'])} |",
            f"| Outcomes valid / corrected / flagged / downgraded / judgement changed / correction failed / format violation | {o['valid']} / {o['corrected']} / {o['flagged']} / {o['downgraded']} / {o['judgement_changed']} / {o['correction_failed']} / {o['format_violation']} |",
            f"| Model calls (mean steps per item) | {loop['model_calls']} ({loop['mean_steps']}) |",
            f"| First-answer error codes | {', '.join(f'{k} {v}' for k, v in loop['initial_error_codes'].items()) or '-'} |",
            f"| Error codes left after the loop | {', '.join(f'{k} {v}' for k, v in loop['final_error_codes'].items()) or '-'} |",
            "",
        ]
    return "\n".join(lines)


def run(items: list[GoldenItem], provider: RecordingProvider, config: RunConfig, runs_dir: Path, *, price_in=None, price_out=None,
        precedent_set=None) -> tuple[Path, bool]:
    """Returns (run directory, reproduced) where reproduced=True means an identical run already existed.
    Live-mode runs are not recorded, hence not reproducible, so they go to runs_dir/_live/<run_id>
    and may be overwritten; only replay/record runs are append-only facts."""
    golden_sha = hashlib.sha256(jsonl_bytes(items)).hexdigest()
    identity = config.identity(golden_sha)
    if price_in is not None or price_out is not None:
        identity["pricing_usd_per_mtok"] = {"input": str(price_in), "output": str(price_out)}
    run_id = canonical_hash(identity)[:16]
    if (config.precedents_k is None) != (precedent_set is None):
        raise ValueError("precedents_k and precedent_set go together")
    per_item = (lambda item: None) if precedent_set is None else (lambda item: precedent_set.by_item[item.item_id])  # noqa: E731
    if config.loop_max_steps is None:
        results = [evaluate_item(item, provider.review(item.item_id, item.input, per_item(item)), config) for item in items]
    else:
        results = [evaluate_loop_item(item, provider, config, per_item(item)) for item in items]
    card = {
        "run_id": run_id,
        "run": identity,
        "scorecard": metrics.scorecard(results, price_in_per_mtok=price_in, price_out_per_mtok=price_out),
    }
    files = {
        "scorecard.json": json.dumps(card, indent=2, sort_keys=True, ensure_ascii=False, default=_json_default) + "\n",
        "scorecard.md": render_markdown(json.loads(json.dumps(card, default=_json_default))),
        "results.jsonl": "".join(canonical_json(r) + "\n" for r in results),
    }
    if provider.mode == "live":
        run_dir = runs_dir / "_live" / run_id
        run_dir.mkdir(parents=True, exist_ok=True)
        for name, content in files.items():
            (run_dir / name).write_text(content, encoding="utf-8")
        return run_dir, False
    run_dir = runs_dir / run_id
    if run_dir.exists():
        for name, content in files.items():
            existing = (run_dir / name).read_text(encoding="utf-8") if (run_dir / name).exists() else None
            if existing != content:
                raise RunConflict(f"{run_dir/name} exists with different content; eval runs are append-only")
        return run_dir, True
    run_dir.mkdir(parents=True)
    for name, content in files.items():
        (run_dir / name).write_text(content, encoding="utf-8")
    return run_dir, False
