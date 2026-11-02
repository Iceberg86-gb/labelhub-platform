"""Command line: `python -m labelhub_evals <command>`."""

from __future__ import annotations

import argparse
from decimal import Decimal

from labelhub_evals import providers, runner
from labelhub_evals.datasets import helpsteer2
from labelhub_evals.paths import RECORDINGS_DIR
from labelhub_evals.sut import ReviewRule


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="labelhub_evals", description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("build-smoke", help="re-derive datasets/helpsteer2-smoke.jsonl from the pinned HelpSteer2 revision")

    run = sub.add_parser("run", help="evaluate the single-call baseline")
    run.add_argument("--dataset", default="helpsteer2-smoke", choices=["helpsteer2-smoke", "helpsteer2-full"])
    run.add_argument("--model", default="deepseek-v4-flash")
    run.add_argument("--prompt", default="helpsteer2-review.v1", help="business prompt in evals/prompts/<name>.md")
    run.add_argument("--provider", default="replay", choices=["replay", "record", "live", "fake"],
                     help="replay (default, offline, free), record (live + save), live, fake (noise, for wiring)")
    run.add_argument("--base-url", help="OpenAI-compatible base URL (default $LABELHUB_EVAL_BASE_URL or DeepSeek)")
    run.add_argument("--pass-threshold", default="0.80")
    run.add_argument("--reject-threshold", default="0.20")
    run.add_argument("--limit", type=int)
    run.add_argument("--concurrency", type=int, default=4)
    run.add_argument("--label", help="suffix for the run directory instead of the config hash")

    args = parser.parse_args(argv)
    if args.command == "build-smoke":
        print(helpsteer2.write_smoke())
        return 0

    rule = ReviewRule(args.prompt, pass_threshold=Decimal(args.pass_threshold), reject_threshold=Decimal(args.reject_threshold))
    cassette = f"{args.dataset}__{args.model}__{args.prompt}"
    provider = providers.from_args(args.provider, RECORDINGS_DIR, cassette, args.base_url)
    run_dir = runner.run(args.dataset, rule, args.model, provider, limit=args.limit,
                         concurrency=args.concurrency, label=args.label)
    print(run_dir / "report.md")
    return 0
