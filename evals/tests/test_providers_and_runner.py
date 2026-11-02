import json

import pytest

from labelhub_evals import runner
from labelhub_evals.providers import FakeProvider, ProviderError, RecordingProvider
from labelhub_evals.sut import ReviewRule

RULE = ReviewRule("helpsteer2-review.v1")


class _CountingProvider(FakeProvider):
    def __init__(self):
        self.calls = 0

    def complete(self, request):
        self.calls += 1
        return super().complete(request)


def test_replay_returns_recorded_exchange_and_never_calls_out(tmp_path):
    request = {"model": "m", "messages": [{"role": "user", "content": "hi"}]}
    inner = _CountingProvider()
    recorded = RecordingProvider("record", tmp_path, "cassette", inner).complete(request)

    replayed = RecordingProvider("replay", tmp_path, "cassette").complete(request)

    assert inner.calls == 1
    assert replayed.response == recorded.response and replayed.usage == recorded.usage


def test_replay_miss_is_an_error_not_a_network_call(tmp_path):
    with pytest.raises(ProviderError) as error:
        RecordingProvider("replay", tmp_path, "empty").complete({"model": "m", "messages": []})
    assert error.value.code == "replay_miss"


def test_replayed_run_reproduces_the_recorded_run_exactly(tmp_path):
    cassettes = tmp_path / "cassettes"
    first = runner.run("helpsteer2-smoke", RULE, "deepseek-v4-flash",
                       RecordingProvider("record", cassettes, "smoke", FakeProvider()),
                       runs_dir=tmp_path / "runs", label="recorded", concurrency=8)
    second = runner.run("helpsteer2-smoke", RULE, "deepseek-v4-flash",
                        RecordingProvider("replay", cassettes, "smoke"),
                        runs_dir=tmp_path / "runs", label="replayed", concurrency=8)

    assert (first / "results.jsonl").read_text() == (second / "results.jsonl").read_text()
    assert json.loads((first / "summary.json").read_text()) == json.loads((second / "summary.json").read_text())


def test_run_directories_are_append_only(tmp_path):
    kwargs = {"runs_dir": tmp_path, "label": "same", "limit": 2}
    runner.run("helpsteer2-smoke", RULE, "deepseek-v4-flash", FakeProvider(), **kwargs)
    with pytest.raises(FileExistsError):
        runner.run("helpsteer2-smoke", RULE, "deepseek-v4-flash", FakeProvider(), **kwargs)


def test_provider_failure_routes_to_a_human(tmp_path):
    run_dir = runner.run("helpsteer2-smoke", RULE, "deepseek-v4-flash", RecordingProvider("replay", tmp_path / "none", "x"),
                         runs_dir=tmp_path / "runs", label="failing", limit=3)
    rows = [json.loads(line) for line in (run_dir / "results.jsonl").read_text().splitlines()]

    assert {r["route"] for r in rows} == {"manual_review"}
    assert {r["error"] for r in rows} == {"provider:replay_miss"}
