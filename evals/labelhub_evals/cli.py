"""Command line: python -m labelhub_evals <command>. See evals/README.md."""

from __future__ import annotations

import argparse
import os
import sys
import webbrowser
from decimal import Decimal
from pathlib import Path

from labelhub_evals import golden, review
from labelhub_evals.providers.base import ProviderError
from labelhub_evals.recording import MODES, RecordingProvider, RecordingStore, ReplayMiss
from labelhub_evals.runner import RunConfig, RunConflict, run

EVALS_ROOT = Path(__file__).resolve().parents[1]
DATASET_DIR = EVALS_ROOT / "datasets" / "helpsteer2-h1"

# A fixed, harmless input for `chatgpt test`: the probe proves the draft model accepts the forced tool call.
PROBE_INPUT = {"prompt": "What is the capital of France?", "response": "The capital of France is Paris."}


# --- golden / run ----------------------------------------------------------------------------------
def cmd_golden_build(args) -> int:
    manifest = golden.build(DATASET_DIR, EVALS_ROOT / ".cache", seed=args.seed, size=args.size, check=not args.force)
    print(f"golden set {manifest['name']}: {manifest['size']} items, pass/reject={manifest['label_counts']['pass']}/{manifest['label_counts']['reject']}")
    print(f"golden sha256 {manifest['golden_sha256']}")
    return 0


def _live_provider(args):
    if args.provider == "fake":
        from labelhub_evals.providers.fake import FakeProvider

        return FakeProvider()
    if args.provider == "openai-compatible":
        from labelhub_evals.providers.openai_compat import OpenAiCompatibleProvider

        if not args.model:
            raise SystemExit("--model is required for openai-compatible")
        key = os.environ.get(args.api_key_env)
        if not key:
            raise SystemExit(f"set {args.api_key_env} (API keys are only read from the environment)")
        return OpenAiCompatibleProvider(args.base_url, key, args.model)
    if args.provider == "chatgpt":
        session, http, _, profiles = _chatgpt_context()
        from labelhub_evals.chatgpt.responses import ChatGPTProvider, ResponsesClient

        if args.model:
            model, effort = args.model, args.effort
        else:
            active = profiles.active()
            if active is None:
                raise SystemExit("no applied ChatGPT model; run `chatgpt select`, `chatgpt test`, `chatgpt apply` (or pass --model)")
            model, effort = active.model, active.effort
        return ChatGPTProvider(ResponsesClient(session, http), model, effort)
    raise SystemExit(f"unknown provider {args.provider}")


def _model_identity(args, live) -> str:
    if live is not None:
        return live.identity
    if args.provider == "fake":
        return "fake/hash-heuristic-v1"
    if args.provider == "chatgpt":
        if args.model:
            return f"chatgpt/{args.model}" + (f"@{args.effort}" if args.effort else "")
        from labelhub_evals.chatgpt.config import home_dir
        from labelhub_evals.chatgpt.profile import ProfileStore

        active = ProfileStore(home_dir()).active()
        if active is None:
            raise SystemExit("replay needs --model (or an applied ChatGPT profile) to locate recordings")
        return active.identity()
    if not args.model:
        raise SystemExit("--model is required")
    return f"{args.provider}/{args.model}"


def cmd_run(args) -> int:
    items = golden.load(Path(args.golden))
    live = None if args.mode == "replay" else _live_provider(args)
    model = _model_identity(args, live)
    provider = RecordingProvider(live, RecordingStore(Path(args.recordings_dir)), args.mode, model, review.PROMPT_VERSION)
    config = RunConfig(golden_name=Path(args.golden).stem if args.golden_name is None else args.golden_name, model=model,
                       loop_max_steps=args.loop_max_steps, correction_style=args.correction_style)
    price_in = Decimal(args.price_in) if args.price_in else None
    price_out = Decimal(args.price_out) if args.price_out else None
    try:
        run_dir, reproduced = run(items, provider, config, Path(args.runs_dir), price_in=price_in, price_out=price_out)
    except ReplayMiss as exc:
        print(f"replay miss: {exc}\nrecord first with --mode record", file=sys.stderr)
        return 2
    except RunConflict as exc:
        print(f"NOT REPRODUCED: {exc}", file=sys.stderr)
        return 3
    except ProviderError as exc:
        print(f"provider error: {exc}", file=sys.stderr)
        return 4
    print((run_dir / "scorecard.md").read_text(encoding="utf-8"))
    print(f"{'reproduced existing run' if reproduced else 'wrote new run'}: {run_dir}  (recording hits {provider.hits}, model calls {provider.misses})")
    if args.require_reproduced and not reproduced:
        print("expected an existing identical run (--require-reproduced)", file=sys.stderr)
        return 3
    return 0


def cmd_analyze(args) -> int:
    from labelhub_evals.analyze import run_analysis

    text = run_analysis(Path(args.run_dir), golden.load(Path(args.golden)))
    if args.out:
        out = Path(args.out)
        if args.check:
            if not out.exists() or out.read_text(encoding="utf-8") != text:
                print(f"{out} is stale; regenerate with `analyze {args.run_dir} --out {args.out}`", file=sys.stderr)
                return 3
            print(f"{out} is up to date")
            return 0
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(text, encoding="utf-8")
        print(f"wrote {out}")
    else:
        print(text)
    return 0


def cmd_compare(args) -> int:
    from labelhub_evals.compare import CompareError, compare

    try:
        text, passed = compare(Path(args.baseline), Path(args.candidate))
    except CompareError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    if args.out:
        out = Path(args.out)
        if args.check:
            if not out.exists() or out.read_text(encoding="utf-8") != text:
                print(f"{out} is stale; regenerate with `compare {args.baseline} {args.candidate} --out {args.out}`", file=sys.stderr)
                return 3
        else:
            out.parent.mkdir(parents=True, exist_ok=True)
            out.write_text(text, encoding="utf-8")
    print(text)
    return 0 if passed else 1


def cmd_calibrate(args) -> int:
    from labelhub_evals.calibrate import load, render

    run_dir = Path(args.run_dir)
    text = render(run_dir.name, load(run_dir), [Decimal(e) for e in args.reviewer_error], [Decimal(b) for b in args.budget])
    if args.out:
        out = Path(args.out)
        if args.check:
            if not out.exists() or out.read_text(encoding="utf-8") != text:
                print(f"{out} is stale; regenerate with `calibrate {args.run_dir} --out {args.out}`", file=sys.stderr)
                return 3
            print(f"{out} is up to date")
            return 0
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(text, encoding="utf-8")
        print(f"wrote {out}")
    else:
        print(text)
    return 0


# --- chatgpt ---------------------------------------------------------------------------------------
def _chatgpt_context():
    from labelhub_evals.chatgpt.config import Endpoints, home_dir
    from labelhub_evals.chatgpt.credentials import CredentialsStore
    from labelhub_evals.chatgpt.http import Http
    from labelhub_evals.chatgpt.profile import ProfileStore
    from labelhub_evals.chatgpt.session import ChatGPTSession

    home = home_dir()
    endpoints = Endpoints.from_env()
    http = Http()
    store = CredentialsStore(home)
    return ChatGPTSession(store, http, endpoints), http, home, ProfileStore(home)


def cmd_login(args) -> int:
    from labelhub_evals.chatgpt import oauth

    session, http, home, _ = _chatgpt_context()
    registration = oauth.Registration.load(home)
    previous = session.store.load()
    attempt = oauth.AuthAttempt.new()
    with oauth.CallbackServer(args.port) as server:  # listener first, then the browser
        url = oauth.authorize_url(session.endpoints, registration, attempt, server.redirect_uri,
                                  id_token_hint=previous.id_token if previous else None,
                                  login_hint=previous.email if previous else None)
        if server.port != args.port:
            print(f"port {args.port} is busy; listening on {server.port} instead")
        print("Opening the OpenAI sign-in page. Enter your password there, not here.")
        print(f"If no browser opens, visit:\n  {oauth.redact_url(url) if previous else url}")
        if not args.no_browser:
            webbrowser.open(url)
        result = server.wait(args.timeout)
    try:
        code, client_id = oauth.validate_callback(result, attempt, registration)
        creds = oauth.exchange_code(http, session.endpoints, client_id=client_id, code=code, attempt=attempt, redirect_uri=server.redirect_uri)
    except oauth.OAuthError as exc:
        print(f"login failed: {exc}", file=sys.stderr)
        return 1
    if registration.client_id != client_id:
        registration.client_id = client_id
        registration.save(home)
    session.store.save(creds)
    print(f"signed in as {creds.email or creds.account_key}; tokens encrypted at {session.store.path} (key: {session.store.key_backend()})")
    return 0


def cmd_logout(args) -> int:
    from labelhub_evals.chatgpt import oauth

    session, http, _, _ = _chatgpt_context()
    try:
        creds = session.store.load()
    except RuntimeError as exc:
        # Unreadable local tokens (key gone from the keychain, or corrupted): nothing to revoke remotely,
        # but logout must still clear them, or the user can never log in again.
        session.store.delete()
        print(f"signed out (local tokens were unreadable and have been deleted: {exc})")
        return 0
    if creds is None:
        print("not signed in")
        return 0
    revoked = oauth.revoke(http, session.endpoints, creds)
    session.store.delete()
    print("signed out" + ("" if revoked else " (local tokens deleted; remote revocation did not confirm)"))
    return 0


def cmd_status(args) -> int:
    import time

    session, _, _, profiles = _chatgpt_context()
    creds = session.store.load()
    if creds is None:
        print("not signed in")
    else:
        print(f"account: {creds.email or '-'} ({creds.account_key})")
        print(f"access token expires in {int(creds.expires_at - time.time())}s; scopes: {' '.join(creds.scopes)}")
    profile = profiles.load()
    print(f"active model: {profiles.active().identity() if profile['active'] else '-'}")
    if profile["draft"]:
        tested = "passed" if profile["test"] and profile["test"]["passed"] else "not tested"
        print(f"draft: {profile['draft']['model']}@{profile['draft']['effort'] or 'default'} ({profile['draft']['source']}, {tested})")
    print(f"rollback points: {len(profile['rollback'])}")
    return 0


def _catalog():
    from labelhub_evals.chatgpt.models import fetch_catalog

    session, http, _, _ = _chatgpt_context()
    return fetch_catalog(session, http, session.endpoints)


def cmd_models(args) -> int:
    import json

    from labelhub_evals.chatgpt.models import diagnose_catalog, parse_catalog, ranked

    session, http, _, _ = _chatgpt_context()
    from labelhub_evals.chatgpt.models import fetch_catalog_raw

    payload = fetch_catalog_raw(session, http, session.endpoints)
    if args.raw:
        # Model metadata only: the access token is in the request header, never in this body.
        print(json.dumps(payload, indent=2, ensure_ascii=False, sort_keys=True))
        return 0
    models = parse_catalog(payload)
    if not models:
        print("no usable model found in the catalog: " + diagnose_catalog(payload))
        print("send the output of `chatgpt models --raw` to the maintainer, or pick a model directly:")
        print("  python -m labelhub_evals chatgpt select MODEL --skip-catalog")
        return 1
    order = ranked(models)
    _, _, _, profiles = _chatgpt_context()
    active = profiles.active()
    for i, m in enumerate(order):
        tags = (["latest"] if i == 0 else []) + (["active"] if active and active.model == m.slug else [])
        efforts = ",".join(m.reasoning_efforts) or "-"
        print(f"{m.slug:32} {m.display_name:28} efforts={efforts:28} priority={m.priority if m.priority is not None else '-'} {' '.join(tags)}")
    hidden = len(models) - len(order)
    if hidden:
        print(f"({hidden} model(s) need a newer client version)")
    return 0


def cmd_select(args) -> int:
    from labelhub_evals.chatgpt.models import pick_latest
    from labelhub_evals.chatgpt.profile import Selection

    session, _, _, profiles = _chatgpt_context()
    creds = session.credentials()
    if args.skip_catalog:
        # Escape hatch when the catalog cannot be read; `chatgpt test` still proves the model works.
        if args.auto_latest or not args.model:
            raise SystemExit("--skip-catalog needs an explicit MODEL (and is incompatible with --auto-latest)")
        profiles.select(creds.account_key, Selection(args.model, args.effort), "manual-skip-catalog")
        print(f"draft: {args.model}@{args.effort or 'default'} (catalog not checked). Next: `chatgpt test`, then `chatgpt apply`.")
        return 0
    models = _catalog()
    if args.auto_latest:
        chosen = pick_latest(models)
        model, source = chosen.slug, "auto-latest"
    else:
        if not args.model:
            raise SystemExit("give a model slug or --auto-latest")
        chosen = next((m for m in models if m.slug == args.model), None)
        if chosen is None:
            raise SystemExit(f"{args.model} is not in this account's catalog; see `chatgpt models`")
        model, source = chosen.slug, "manual"
    effort = args.effort or chosen.default_effort
    if not chosen.supports_effort(effort):
        raise SystemExit(f"{model} does not support effort {effort!r}; supported: {', '.join(chosen.reasoning_efforts)}")
    profiles.select(creds.account_key, Selection(model, effort), source)
    print(f"draft: {model}@{effort or 'default'} ({source}). Next: `chatgpt test`, then `chatgpt apply`.")
    return 0


def cmd_test(args) -> int:
    from labelhub_evals.chatgpt.responses import ChatGPTProvider, ResponsesClient

    session, http, _, profiles = _chatgpt_context()
    creds = session.credentials()
    draft = profiles.load()["draft"]
    if draft is None:
        raise SystemExit("no draft; run `chatgpt select` first")
    provider = ChatGPTProvider(ResponsesClient(session, http), draft["model"], draft["effort"])
    try:
        response = provider.review(PROBE_INPUT)
        if response.violation:
            raise review.FormatViolation(response.violation)
        review.parse_tool_arguments(response.arguments or "")
        passed, detail = True, f"record_ai_review ok in {response.latency_ms}ms"
    except (ProviderError, review.FormatViolation) as exc:
        passed, detail = False, str(exc)
    profiles.record_test(creds.account_key, passed, detail)
    print(("PASS: " if passed else "FAIL: ") + detail + ("" if passed else "\ncurrent configuration kept"))
    return 0 if passed else 1


def cmd_apply(args) -> int:
    from labelhub_evals.chatgpt.profile import ProfileError

    session, _, _, profiles = _chatgpt_context()
    try:
        profile = profiles.apply(session.credentials().account_key)
    except ProfileError as exc:
        print(f"not applied: {exc}", file=sys.stderr)
        return 1
    print(f"applied chatgpt/{profile['active']['model']}@{profile['active']['effort'] or 'default'} (rollback points: {len(profile['rollback'])})")
    return 0


def cmd_rollback(args) -> int:
    from labelhub_evals.chatgpt.profile import ProfileError

    session, _, _, profiles = _chatgpt_context()
    try:
        profile = profiles.rollback(session.credentials().account_key)
    except ProfileError as exc:
        print(f"rollback failed: {exc}", file=sys.stderr)
        return 1
    print(f"rolled back to chatgpt/{profile['active']['model']}@{profile['active']['effort'] or 'default'}")
    return 0


def cmd_check_latest(args) -> int:
    from labelhub_evals.chatgpt.models import pick_latest

    _, _, _, profiles = _chatgpt_context()
    latest = pick_latest(_catalog())
    active = profiles.active()
    if active and active.model == latest.slug:
        print(f"up to date: {latest.slug}")
        return 0
    print(f"newer recommended model available: {latest.slug} (active: {active.model if active else '-'})")
    print("eval runs keep the pinned model; to switch: `chatgpt select --auto-latest`, `chatgpt test`, `chatgpt apply`")
    return 10


def cmd_ask(args) -> int:
    from labelhub_evals.chatgpt.responses import ChatGPTProvider, ResponsesClient

    session, http, _, profiles = _chatgpt_context()
    active = profiles.active()
    if active is None:
        raise SystemExit("no applied model; run `chatgpt select/test/apply` first")
    provider = ChatGPTProvider(ResponsesClient(session, http), active.model, active.effort)
    try:
        provider.ask(args.prompt, lambda delta: (sys.stdout.write(delta), sys.stdout.flush()))
    except ProviderError as exc:
        print(f"\nerror: {exc}", file=sys.stderr)
        return 1
    print()
    return 0


# --- parser ----------------------------------------------------------------------------------------
def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="labelhub_evals", description="LabelHub offline evals (ADR-017 H1)")
    sub = parser.add_subparsers(dest="command", required=True)

    g = sub.add_parser("golden", help="golden set").add_subparsers(dest="golden_cmd", required=True)
    gb = g.add_parser("build", help="download pinned HelpSteer2 and rebuild the golden set deterministically")
    gb.add_argument("--seed", type=int, default=golden.DEFAULT_SEED, help="only used with --size")
    gb.add_argument("--size", type=int, default=golden.DEFAULT_SIZE, help="subsample N rows (default: all rows)")
    gb.add_argument("--force", action="store_true", help="allow a manifest change (new seed/size/rule)")
    gb.set_defaults(fn=cmd_golden_build)

    r = sub.add_parser("run", help="run one evaluation")
    r.add_argument("--golden", default=str(DATASET_DIR / "golden.jsonl"))
    r.add_argument("--golden-name", default=None)
    r.add_argument("--provider", choices=["fake", "openai-compatible", "chatgpt"], required=True)
    r.add_argument("--mode", choices=MODES, default="record")
    r.add_argument("--model")
    r.add_argument("--effort")
    r.add_argument("--base-url", default="https://api.openai.com/v1")
    r.add_argument("--api-key-env", default="OPENAI_API_KEY")
    r.add_argument("--price-in", help="USD per 1M input tokens (for cost_per_item)")
    r.add_argument("--price-out", help="USD per 1M output tokens")
    r.add_argument("--runs-dir", default=str(EVALS_ROOT / "runs"))
    r.add_argument("--recordings-dir", default=str(EVALS_ROOT / "recordings"))
    r.add_argument("--loop-max-steps", type=int, default=None, help="ADR-019 H2: validate-and-correct with at most N model calls per item")
    r.add_argument("--correction-style", choices=["tool", "text"], default="tool",
                   help="how failed calls are replayed to the model (text: fallback if replayed tool calls are rejected)")
    r.add_argument("--require-reproduced", action="store_true", help="fail unless an identical run already exists (CI gate)")
    r.set_defaults(fn=cmd_run)

    an = sub.add_parser("analyze", help="error analysis of a run against its golden set")
    an.add_argument("run_dir")
    an.add_argument("--golden", default=str(DATASET_DIR / "golden.jsonl"))
    an.add_argument("--out", help="write the markdown report here")
    an.add_argument("--check", action="store_true", help="fail if --out differs from a fresh analysis (CI)")
    an.set_defaults(fn=cmd_analyze)

    cp = sub.add_parser("compare", help="compare a candidate run with a baseline (exit 1 if the gate fails)")
    cp.add_argument("baseline")
    cp.add_argument("candidate")
    cp.add_argument("--out")
    cp.add_argument("--check", action="store_true")
    cp.set_defaults(fn=cmd_compare)

    cal = sub.add_parser("calibrate", help="ADR-020: escalation-rate vs bad-export frontier over threshold policies (replay only)")
    cal.add_argument("run_dir")
    cal.add_argument("--reviewer-error", nargs="+", default=["0.05", "0.10", "0.20"])
    cal.add_argument("--budget", nargs="+", default=["0.15", "0.25", "0.35", "0.45"], help="escalation-rate budgets")
    cal.add_argument("--out")
    cal.add_argument("--check", action="store_true")
    cal.set_defaults(fn=cmd_calibrate)

    c = sub.add_parser("chatgpt", help="Sign in with ChatGPT (ADR-018)").add_subparsers(dest="chatgpt_cmd", required=True)
    login = c.add_parser("login")
    login.add_argument("--port", type=int, default=1455)
    login.add_argument("--timeout", type=float, default=300)
    login.add_argument("--no-browser", action="store_true")
    login.set_defaults(fn=cmd_login)
    c.add_parser("logout").set_defaults(fn=cmd_logout)
    c.add_parser("status").set_defaults(fn=cmd_status)
    models_p = c.add_parser("models", help="list the account's model catalog, recommended first")
    models_p.add_argument("--raw", action="store_true", help="print the catalog JSON as returned (for debugging)")
    models_p.set_defaults(fn=cmd_models)
    sel = c.add_parser("select", help="write a draft model selection")
    sel.add_argument("model", nargs="?")
    sel.add_argument("--effort")
    sel.add_argument("--auto-latest", action="store_true", help="resolve the recommended latest model now and pin it")
    sel.add_argument("--skip-catalog", action="store_true", help="select MODEL without checking the catalog (test still required)")
    sel.set_defaults(fn=cmd_select)
    c.add_parser("test", help="make one real request with the draft").set_defaults(fn=cmd_test)
    c.add_parser("apply", help="apply a tested draft (saves a rollback point)").set_defaults(fn=cmd_apply)
    c.add_parser("rollback").set_defaults(fn=cmd_rollback)
    c.add_parser("check-latest", help="report (never switch) when a newer recommended model exists").set_defaults(fn=cmd_check_latest)
    ask = c.add_parser("ask", help="stream an answer from the applied model")
    ask.add_argument("prompt")
    ask.set_defaults(fn=cmd_ask)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        return args.fn(args)
    except Exception as exc:  # surface a clean message; tokens never appear in exception text
        from labelhub_evals.chatgpt.oauth import OAuthError
        from labelhub_evals.chatgpt.session import NotSignedIn

        from labelhub_evals.chatgpt.http import HttpError

        if isinstance(exc, (NotSignedIn, OAuthError, ProviderError, HttpError, ValueError)):
            print(f"error: {exc}", file=sys.stderr)
            return 1
        raise
