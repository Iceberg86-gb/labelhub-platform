"""Run one eval and write an append-only run directory: manifest.json, results.jsonl, summary.json, report.md."""

from __future__ import annotations

import json
import platform
import subprocess
from concurrent.futures import ThreadPoolExecutor
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

from labelhub_evals import __version__, contracts, metrics, report
from labelhub_evals.canonical import sha256_json
from labelhub_evals.datasets import helpsteer2
from labelhub_evals.paths import REPO_ROOT, RUNS_DIR
from labelhub_evals.pricing import price_table
from labelhub_evals.providers import Provider, ProviderError, request_key
from labelhub_evals.sut import ReviewRule, build_request, parse_response


def _git(*args: str) -> str | None:
    try:
        return subprocess.run(["git", *args], cwd=REPO_ROOT, capture_output=True, text=True, check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return None


def _evaluate(case: helpsteer2.Case, rule: ReviewRule, model: str, provider: Provider) -> dict[str, Any]:
    request = build_request(case, rule, model)
    row: dict[str, Any] = {"case_id": case.case_id, "truth": case.truth, "request_key": request_key(request)}
    try:
        completion = provider.complete(request)
    except ProviderError as error:
        # Same deterministic fallback as production: a failed call goes to a human, never to auto-pass.
        return row | {"route": "manual_review", "final_score": None, "error": f"provider:{error.code}", "warnings": [],
                      "latency_ms": None, "usage": None, "cost_usd": None}
    review = parse_response(completion.response, rule)
    price = price_table().get(model)
    cost = price.cost(**completion.usage) if price else None
    return row | {
        "route": review.route,
        "final_score": str(review.final_score) if review.final_score is not None else None,
        "overall_suggestion": review.overall_suggestion,
        "confidence": review.confidence,
        "dimension_scores": review.dimension_scores,
        "error": review.error,
        "warnings": review.warnings,
        "latency_ms": completion.latency_ms,
        "usage": completion.usage,
        "cost_usd": str(cost) if cost is not None else None,
    }


def run(dataset: str, rule: ReviewRule, model: str, provider: Provider, *, limit: int | None = None,
        concurrency: int = 4, runs_dir: Path = RUNS_DIR, label: str | None = None) -> Path:
    cases = helpsteer2.load(dataset)
    if limit:
        cases = cases[:limit]
    started = datetime.now(UTC)
    manifest = {
        "harnessVersion": __version__,
        "system": "single-call-baseline",
        "dataset": {"name": dataset, "cases": len(cases), "caseSetSha256": sha256_json([c.case_id for c in cases]),
                    "source": f"{helpsteer2.REPO}@{helpsteer2.REVISION}", "labelMapping": helpsteer2.LABEL_MAPPING_VERSION},
        "rule": rule.describe(),
        "model": model,
        "provider": provider.name,
        "toolContractSha256": sha256_json(contracts.tool_definition()),
        "git": {"commit": _git("rev-parse", "HEAD"), "dirty": bool(_git("status", "--porcelain"))},
        "python": platform.python_version(),
        "startedAt": started.isoformat(),
    }
    config_hash = sha256_json({k: manifest[k] for k in ("dataset", "rule", "model", "provider", "toolContractSha256")})[:8]
    run_dir = runs_dir / f"{started:%Y%m%dT%H%M%SZ}__{dataset}__{model}__{label or config_hash}"
    run_dir.mkdir(parents=True, exist_ok=False)  # append-only: never overwrite an earlier run

    with ThreadPoolExecutor(max_workers=max(1, concurrency)) as pool:
        results = list(pool.map(lambda case: _evaluate(case, rule, model, provider), cases))

    manifest["finishedAt"] = datetime.now(UTC).isoformat()
    manifest["configSha256"] = config_hash
    summary = metrics.summarize(results)
    (run_dir / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    with open(run_dir / "results.jsonl", "w", encoding="utf-8") as handle:
        for row in results:
            handle.write(json.dumps(row, ensure_ascii=False, sort_keys=True) + "\n")  # byte-stable across record/replay
    (run_dir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (run_dir / "report.md").write_text(report.render(manifest, summary), encoding="utf-8")
    return run_dir
