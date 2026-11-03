"""ADR-019 contracts of the evals H2 loop: prompt parity with the agent, loop branches, recording reuse,
H1 identity stability, and the comparison gate."""

from __future__ import annotations

import json
from decimal import Decimal
from pathlib import Path

import pytest

from labelhub_evals import golden, review
from labelhub_evals.compare import CompareError, compare
from labelhub_evals.providers.base import ProviderResponse
from labelhub_evals.recording import RecordingProvider, RecordingStore, ReplayMiss
from labelhub_evals.review_loop import FEEDBACK_VERSION, FIXTURE, Attempt, render_feedback, run_loop
from labelhub_evals.review_validation import ValidationContext, ValidationError
from labelhub_evals.runner import RunConfig, evaluate_loop_item, run

CTX = ValidationContext(("overall",), Decimal("0.80"), Decimal("0.20"), frozenset({"prompt", "response"}))


def out(sugg, score, findings=()):
    return json.dumps({"overallSuggestion": sugg, "confidence": score, "summary": "s",
                       "dimensionScores": [{"dimension": "overall", "score": score}], "fieldFindings": list(findings)})


class Scripted:
    """Answers step 1 with first[], corrections with the next items of fixes[]; records what it was sent."""

    identity = "scripted/1"

    def __init__(self, first, *fixes):
        self.first, self.fixes, self.corrections = first, list(fixes), []

    def review(self, review_input):
        return ProviderResponse(self.first, None, 100, 10, 1000)

    def correct(self, review_input, attempts, style):
        self.corrections.append((len(attempts), style, attempts[-1].feedback))
        return ProviderResponse(self.fixes.pop(0), None, 150, 15, 2000)


def recorder(tmp_path, provider, mode="record"):
    return RecordingProvider(provider, RecordingStore(tmp_path), mode, provider.identity if provider else "scripted/1", review.PROMPT_VERSION)


# --- prompt parity -------------------------------------------------------------------------------------
def test_feedback_renders_byte_identical_to_the_shared_fixture_used_by_the_agent():
    fixture = json.loads(FIXTURE.read_text(encoding="utf-8"))
    errors = [ValidationError(e["code"], e["path"], e["message"]) for e in fixture["example"]["errors"]]
    assert FEEDBACK_VERSION == fixture["promptVersion"]
    assert render_feedback(errors) == fixture["example"]["rendered"]


def test_validator_messages_match_the_java_wording_used_in_feedback():
    from labelhub_evals.review_validation import validate

    errors = validate(json.loads(out("reject", 0.35)), CTX)
    assert errors[0].message == ("overallSuggestion reject contradicts the scores: final score 0.3500 routes to manual_review "
                                 "(pass >= 0.80, reject <= 0.20); change the suggestion or the scores so they agree")


# --- loop branches -------------------------------------------------------------------------------------
def test_valid_first_answer_needs_one_call(tmp_path):
    p = Scripted(out("pass", 0.9))
    result = run_loop(recorder(tmp_path, p), "h", {"prompt": "p", "response": "r"}, CTX, 3)
    assert (result.outcome, result.steps, p.corrections) == ("valid", 1, [])


def test_conflict_is_fed_back_and_corrected(tmp_path):
    p = Scripted(out("reject", 0.35), out("manual_review", 0.35))
    result = run_loop(recorder(tmp_path, p), "h", {"prompt": "p", "response": "r"}, CTX, 3)
    assert (result.outcome, result.steps) == ("corrected", 2)
    n, style, feedback = p.corrections[0]
    assert (n, style) == (1, "tool") and "[suggestion_score_conflict] overallSuggestion" in feedback


def test_never_fixed_is_downgraded_after_max_steps_and_history_grows(tmp_path):
    bad = out("pass", 0.9, [{"fieldPath": "response", "stableId": "response-1", "severity": "info", "finding": "x"}])
    p = Scripted(bad, bad, bad)
    result = run_loop(recorder(tmp_path, p), "h", {"prompt": "p", "response": "r"}, CTX, 3)
    assert (result.outcome, result.steps) == ("downgraded", 3)
    assert [n for n, _, _ in p.corrections] == [1, 2]


def test_format_violation_is_not_looped(tmp_path):
    class NoTool(Scripted):
        def review(self, review_input):
            return ProviderResponse(None, "text answer", 1, 1, 1)

    result = run_loop(recorder(tmp_path, NoTool(None)), "h", {}, CTX, 3)
    assert (result.outcome, result.steps) == ("format_violation", 1)


def test_downgraded_item_keeps_score_routing_and_drops_unknown_findings_and_sums_cost(tmp_path):
    bad = out("pass", 0.9, [{"fieldPath": "response", "stableId": "response-1", "severity": "info", "finding": "x"}])
    item = golden.GoldenItem("h", 0, {"prompt": "p", "response": "r"}, "pass", {})
    row = evaluate_loop_item(item, recorder(tmp_path, Scripted(bad, bad, bad)), RunConfig("g", "scripted/1", loop_max_steps=3))
    assert row["loop_outcome"] == "downgraded" and row["final_error_codes"] == ["unknown_field"]
    assert row["route"] == "pass"  # API is the scoring authority: routing follows the score, as in production
    assert row["model_suggestion"] == "manual_review"
    assert (row["input_tokens"], row["output_tokens"], row["latency_ms"], row["steps"]) == (400, 40, 5000, 3)


# --- recordings ----------------------------------------------------------------------------------------
def test_step_one_reuses_h1_recordings_and_corrections_have_their_own_keys(tmp_path):
    p = Scripted(out("reject", 0.35), out("manual_review", 0.35))
    RecordingProvider(p, RecordingStore(tmp_path), "record", "scripted/1", review.PROMPT_VERSION).review("h", {})  # an H1 run
    replay = RecordingProvider(None, RecordingStore(tmp_path), "replay", "scripted/1", review.PROMPT_VERSION)
    with pytest.raises(ReplayMiss, match="correction turn 1"):
        run_loop(replay, "h", {}, CTX, 3)
    assert replay.hits == 1  # step 1 came from the H1 recording


def test_correction_key_depends_on_attempts_and_style(tmp_path):
    store = RecordingStore(tmp_path)
    p = Scripted(out("pass", 0.9), out("pass", 0.9), out("pass", 0.9), out("pass", 0.9))
    rec = RecordingProvider(p, store, "record", "scripted/1", review.PROMPT_VERSION)
    e = (ValidationError("suggestion_score_conflict", "overallSuggestion", "m"),)
    rec.correct("h", {}, [Attempt("a1", e)], "tool")
    rec.correct("h", {}, [Attempt("a1", e)], "text")
    rec.correct("h", {}, [Attempt("a2", e)], "tool")
    rec.correct("h", {}, [Attempt("a1", e)], "tool")  # identical conversation: cache hit
    assert (rec.misses, rec.hits) == (3, 1)


# --- run identity & end-to-end --------------------------------------------------------------------------
def test_h1_run_identity_has_no_loop_key_so_w1_run_ids_are_stable():
    assert "loop" not in RunConfig("g", "m").identity("x")
    loop = RunConfig("g", "m", loop_max_steps=3).identity("x")["loop"]
    assert loop == {"correction_style": "tool", "feedback_version": "h2-validate-loop-v1", "max_steps": 3, "rules_version": "review-output-v1"}


def test_loop_run_is_reproducible_and_reports_loop_metrics(tmp_path):
    from labelhub_evals.providers.fake import FakeProvider

    items = golden.load(Path(__file__).resolve().parents[1] / "datasets" / "helpsteer2-h1" / "smoke.jsonl")[:8]

    class Conflicted(FakeProvider):
        def review(self, review_input):
            r = super().review(review_input)
            if r.arguments is None:
                return r
            data = json.loads(r.arguments)
            data["overallSuggestion"] = "reject"  # always contradicts unless score <= 0.20
            return ProviderResponse(json.dumps(data), None, r.input_tokens, r.output_tokens, r.latency_ms)

    cfg = RunConfig("smoke8", Conflicted.identity, loop_max_steps=3)
    rec = RecordingProvider(Conflicted(), RecordingStore(tmp_path / "rec"), "record", Conflicted.identity, review.PROMPT_VERSION)
    run_dir, _ = run(items, rec, cfg, tmp_path / "runs")
    card = json.loads((run_dir / "scorecard.json").read_text())["scorecard"]
    assert card["loop"]["outcomes"]["corrected"] > 0
    assert card["validator_correction_success_rate"] == card["loop"]["correction_success_rate"]
    assert "Validate-and-correct loop" in (run_dir / "scorecard.md").read_text()
    replay = RecordingProvider(None, RecordingStore(tmp_path / "rec"), "replay", Conflicted.identity, review.PROMPT_VERSION)
    assert run(items, replay, cfg, tmp_path / "runs") == (run_dir, True)


# --- compare gate --------------------------------------------------------------------------------------
def _card(path: Path, sha: str, ape: str | None, loop=False):
    card = {"run_id": path.name, "run": {"golden": {"name": "g", "sha256": sha}, "model": "m", "prompt_version": "p"},
            "scorecard": {"items": 10, "auto_pass_error_rate": ape, "auto_reject_error_rate": "0.5", "manual_review_rate": "0.3",
                          "decided_agreement": "0.6", "cohen_kappa_decided": "0.3", "format_violation_rate": "0",
                          "routed_counts": {"pass": 1, "manual_review": 1, "reject": 1},
                          "tokens": {"input_total": 1, "output_total": 1}, "latency_ms": {"p50": 1, "p95": 2}}}
    if loop:
        card["run"]["loop"] = {"feedback_version": "h2-validate-loop-v1", "max_steps": 3}
        card["scorecard"]["loop"] = {"initial_invalid_rate": "0.6", "final_invalid_rate": "0.1", "correction_success_rate": "0.8", "model_calls": 17}
    path.mkdir()
    (path / "scorecard.json").write_text(json.dumps(card))
    return path


def test_compare_gate_passes_when_auto_pass_error_does_not_rise_and_fails_when_it_does(tmp_path):
    base = _card(tmp_path / "base", "s", "0.0893")
    same = _card(tmp_path / "same", "s", "0.0893", loop=True)
    worse = _card(tmp_path / "worse", "s", "0.0900")
    text, passed = compare(base, same)
    assert passed and "PASS" in text and "Correction success rate" in text
    assert compare(base, worse)[1] is False


def test_compare_refuses_runs_on_different_golden_sets(tmp_path):
    with pytest.raises(CompareError):
        compare(_card(tmp_path / "a", "s1", "0.1"), _card(tmp_path / "b", "s2", "0.1"))


# --- provider request shapes ---------------------------------------------------------------------------
def test_openai_compatible_correction_matches_the_agent_conversation_shape():
    from labelhub_evals.providers.openai_compat import OpenAiCompatibleProvider

    e = (ValidationError("unknown_field", "fieldFindings[0].stableId", "m"),)
    body = OpenAiCompatibleProvider("http://x", "k", "m").correction_body({"prompt": "p", "response": "r"}, [Attempt("{\"a\":1}", e)], "tool")
    msgs = body["messages"]
    assert [m["role"] for m in msgs] == ["system", "user", "assistant", "tool"]
    assert msgs[2]["tool_calls"][0]["id"] == msgs[3]["tool_call_id"] == "call_review_attempt_1"
    assert msgs[2]["tool_calls"][0]["function"] == {"name": "record_ai_review", "arguments": "{\"a\":1}"}
    assert msgs[3]["content"] == render_feedback(list(e))
    assert body["tool_choice"] == {"type": "function", "function": {"name": "record_ai_review"}}


def test_chatgpt_correction_replays_function_call_and_output_items_or_falls_back_to_text():
    from labelhub_evals.chatgpt.responses import correction_body

    e = (ValidationError("suggestion_score_conflict", "overallSuggestion", "m"),)
    body = correction_body("gpt", "low", {"prompt": "p", "response": "r"}, [Attempt("{\"a\":1}", e)], "tool")
    assert [i.get("type", i.get("role")) for i in body["input"]] == ["user", "function_call", "function_call_output"]
    assert body["input"][1]["call_id"] == body["input"][2]["call_id"] and "id" not in body["input"][1]
    assert body["input"][2]["output"] == render_feedback(list(e))
    assert body["tool_choice"] == {"type": "function", "name": "record_ai_review"}
    text = correction_body("gpt", "low", {"prompt": "p", "response": "r"}, [Attempt("{\"a\":1}", e)], "text")
    assert len(text["input"]) == 1 and "Your record_ai_review call #1 was:" in text["input"][0]["content"][0]["text"]
