"""ADR-019: the Python validator matches the shared contract corpus case by case (code + path, in order)."""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from labelhub_evals.review_validation import CORRECTABLE, FLAG_ONLY, ValidationContext, routing, validate

CORPUS = Path(__file__).resolve().parents[2] / "packages" / "contracts" / "fixtures" / "review-output-validation-corpus.json"
CASES = json.loads(CORPUS.read_text(encoding="utf-8"))


@pytest.mark.parametrize("case", CASES, ids=[c["caseId"] for c in CASES])
def test_matches_contract_corpus(case):
    errors = validate(case["output"], ValidationContext.from_json(case["context"]))
    assert [{"code": e.code, "path": e.path} for e in errors] == case["expectedErrors"]
    assert all(e.message for e in errors)


def test_corpus_covers_every_error_code():
    codes = {e["code"] for c in CASES for e in c["expectedErrors"]}
    assert codes == {"invalid_structure", "missing_dimension", "duplicate_dimension", "unknown_dimension",
                     "unknown_field", "suggestion_score_conflict", "error_finding_on_pass"}


@pytest.mark.parametrize("case", CASES, ids=[c["caseId"] for c in CASES])
def test_routing_matches_contract_corpus(case):
    zone, score = routing(case["output"], ValidationContext.from_json(case["context"]))
    assert zone == case["expectedRoute"]
    assert (None if score is None else str(score)) == case["expectedFinalScore"]


def test_every_code_is_either_correctable_or_flag_only():
    codes = {e["code"] for c in CASES for e in c["expectedErrors"]}
    assert CORRECTABLE | FLAG_ONLY == codes and not CORRECTABLE & FLAG_ONLY
