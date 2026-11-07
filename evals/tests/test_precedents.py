"""ADR-021 (H4): BM25 parity with the API via the shared corpus, prompt block, recording keys, run identity,
and the H4 gate."""

from __future__ import annotations

import json
from decimal import Decimal
from pathlib import Path

import pytest

from labelhub_evals import golden, review
from labelhub_evals.canonical import canonical_hash
from labelhub_evals.gate_h4 import GateError, gate
from labelhub_evals.precedent_pool import PoolDoc, load, pool_docs, retrieve
from labelhub_evals.precedents import (
    EXCERPT_CODE_POINTS,
    FIXTURE,
    HEADER,
    PRECEDENT_VERSION,
    Bm25Index,
    Precedent,
    labeled_text,
    precedents_hash,
    render_block,
    text_of,
    tokenize,
)
from labelhub_evals.providers.fake import FakeProvider
from labelhub_evals.recording import RecordingProvider, RecordingStore, recording_key
from labelhub_evals.runner import RunConfig, run

CORPUS = json.loads(FIXTURE.read_text(encoding="utf-8"))
EVALS = Path(__file__).resolve().parents[1]


@pytest.mark.parametrize("case", CORPUS["tokenize"], ids=lambda c: c["comment"])
def test_tokenizer_matches_shared_corpus(case):
    assert tokenize(case["text"]) == case["tokens"]


@pytest.mark.parametrize("case", CORPUS["textOf"])
def test_text_extraction_matches_shared_corpus(case):
    assert text_of(case["value"]) == case["text"]
    assert labeled_text(case["value"]) == case["labeled"]


def test_bm25_rankings_match_shared_corpus():
    assert CORPUS["version"] == PRECEDENT_VERSION
    index = Bm25Index([(d["key"], text_of(d["content"])) for d in CORPUS["documents"]])
    for q in CORPUS["queries"]:
        assert index.search(q["query"], q["k"], frozenset(q["exclude"])) == q["expected"], q["query"]


def test_ties_break_by_key_and_zero_scores_are_dropped():
    index = Bm25Index([("b", "same words"), ("a", "same words"), ("c", "unrelated")])
    assert index.search("same", 5) == ["a", "b"]
    assert index.search("absent", 5) == []


def test_render_block_matches_shared_corpus_and_escapes_submitted_text():
    r = CORPUS["render"]
    precs = [Precedent(p["key"], p["verdict"], p["source"], p["content"]) for p in r["precedents"]]
    block = render_block(precs)
    assert block == r["block"]
    assert precedents_hash(precs) == r["hash"]
    assert block.startswith(HEADER + "\n[")
    entries = json.loads(block[len(HEADER) + 1:])
    assert [e["rank"] for e in entries] == [1, 2]
    assert entries[1]["submission"].endswith("…") and len(entries[1]["submission"]) == EXCERPT_CODE_POINTS + 1
    # The injected text stays inside one JSON string: the block parses and has exactly two entries.
    assert len(entries) == 2


def test_user_message_without_precedents_is_byte_identical_to_h1():
    item = {"prompt": "p", "response": "r"}
    assert review.user_message(item) == "Business prompt:\n" + review.BUSINESS_PROMPT + "\nInput:\n" + json.dumps(item, ensure_ascii=False, sort_keys=True)
    with_block = review.user_message(item, "BLOCK")
    assert with_block.index(review.BUSINESS_PROMPT) < with_block.index("BLOCK") < with_block.index("Input:")


def test_pool_docs_dedupe_and_label_with_the_golden_rule():
    rows = [
        {"prompt": "p1", "response": "r1", "helpfulness": 3, "correctness": 3},
        {"prompt": "p1", "response": "r1", "helpfulness": 0, "correctness": 0},
        {"prompt": "p1", "response": "r2", "helpfulness": 4, "correctness": 2},
    ]
    docs = pool_docs(rows)
    assert [(d.source_index, d.verdict) for d in docs] == [(0, "approved"), (2, "rejected")]
    assert docs[0].key == canonical_hash({"prompt": "p1", "response": "r1"})


def test_retrieval_excludes_docs_with_the_same_prompt():
    docs = [PoolDoc("k1", 0, {"prompt": "capital of france", "response": "paris"}, "approved"),
            PoolDoc("k2", 1, {"prompt": "capital city of france?", "response": "paris is the capital"}, "approved")]
    item = golden.GoldenItem("i1", 0, {"prompt": "capital of france", "response": "paris"}, "pass", {})
    assert retrieve(docs, [item]) == [{"item_id": "i1", "precedents": ["k2"]}]


def _precedent_set(items):
    return load(EVALS / "datasets" / "helpsteer2-h4", items, 3)


def test_committed_smoke_precedents_resolve_offline():
    items = golden.load(EVALS / "datasets" / "helpsteer2-h1" / "smoke.jsonl")
    ps = _precedent_set(items)
    assert ps.k == 3 and len(ps.by_item) == len(items)
    assert all(len(v) == 3 and all(p.verdict in ("approved", "rejected") for p in v) for v in ps.by_item.values())


def test_precedent_runs_use_their_own_recordings_and_identity(tmp_path):
    items = golden.load(EVALS / "datasets" / "helpsteer2-h1" / "smoke.jsonl")[:6]
    ps = _precedent_set(items)
    store = RecordingStore(tmp_path / "rec")
    rec = RecordingProvider(FakeProvider(), store, "record", "fake/hash-heuristic-v1", review.PROMPT_VERSION)
    base = RunConfig(golden_name="t", model="fake/hash-heuristic-v1", loop_max_steps=3)
    h3_dir, _ = run(items, rec, base, tmp_path / "runs")
    calls_without = rec.misses
    cfg = RunConfig(golden_name="t", model="fake/hash-heuristic-v1", loop_max_steps=3, precedents_k=3, precedent_retrieval_sha256=ps.retrieval_sha256)
    h4_dir, _ = run(items, rec, cfg, tmp_path / "runs", precedent_set=ps)
    assert h4_dir != h3_dir
    assert rec.misses > calls_without  # a precedent prompt never reuses an H1 recording
    ident = json.loads((h4_dir / "scorecard.json").read_text())["run"]
    assert ident["precedents"] == {"k": 3, "retrieval_sha256": ps.retrieval_sha256, "version": PRECEDENT_VERSION}
    first = items[0]
    combined = canonical_hash({"input_hash": first.item_id, "precedents_hash": precedents_hash(ps.by_item[first.item_id])})
    assert store.get(recording_key("fake/hash-heuristic-v1", f"{review.PROMPT_VERSION}+{PRECEDENT_VERSION}", combined)) is not None
    # Replay reproduces the H4 run byte for byte.
    replay = RecordingProvider(None, store, "replay", "fake/hash-heuristic-v1", review.PROMPT_VERSION)
    again, reproduced = run(items, replay, cfg, tmp_path / "runs", precedent_set=ps)
    assert again == h4_dir and reproduced


def test_precedents_k_and_set_go_together(tmp_path):
    items = golden.load(EVALS / "datasets" / "helpsteer2-h1" / "smoke.jsonl")[:1]
    rec = RecordingProvider(FakeProvider(), RecordingStore(tmp_path), "live", "fake/x", review.PROMPT_VERSION)
    with pytest.raises(ValueError):
        run(items, rec, RunConfig(golden_name="t", model="fake/x", precedents_k=3), tmp_path / "runs")


def _fake_run(tmp_path, items, precedents: bool):
    store = RecordingStore(tmp_path / "rec")
    rec = RecordingProvider(FakeProvider(), store, "record", "fake/hash-heuristic-v1", review.PROMPT_VERSION)
    if not precedents:
        return run(items, rec, RunConfig(golden_name="t", model="fake/hash-heuristic-v1", loop_max_steps=3), tmp_path / "runs")[0]
    ps = _precedent_set(items)
    cfg = RunConfig(golden_name="t", model="fake/hash-heuristic-v1", loop_max_steps=3, precedents_k=3, precedent_retrieval_sha256=ps.retrieval_sha256)
    return run(items, rec, cfg, tmp_path / "runs", precedent_set=ps)[0]


def test_gate_h4_reports_and_refuses_wrong_pairs(tmp_path):
    items = golden.load(EVALS / "datasets" / "helpsteer2-h1" / "smoke.jsonl")
    h3, h4 = _fake_run(tmp_path, items, False), _fake_run(tmp_path, items, True)
    text, passed = gate(h3, h4, Decimal("0.45"))
    assert "H4 gate" in text and ("PASS" in text) == passed
    with pytest.raises(GateError):
        gate(h4, h3, Decimal("0.45"))  # reversed: the reference must not be a precedent run
    with pytest.raises(GateError):
        gate(h3, h3, Decimal("0.45"))
