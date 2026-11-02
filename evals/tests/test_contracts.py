"""Parity with packages/contracts/ai. The Java suite asserts against the same files, so a green run on both sides
means the eval request and scoring are the production ones."""

import json
from decimal import Decimal

import pytest

from labelhub_evals import contracts
from labelhub_evals.paths import AI_CONTRACTS_DIR

CORPUS = json.loads((AI_CONTRACTS_DIR / "scoring-corpus.json").read_text(encoding="utf-8"))


def test_prompt_rendering_matches_java_golden_byte_for_byte():
    fixture = json.loads((AI_CONTRACTS_DIR / "prompt-template.fixture.json").read_text(encoding="utf-8"))
    golden = (AI_CONTRACTS_DIR / "prompt-template.golden.txt").read_text(encoding="utf-8")

    assert contracts.render_prompt(fixture["businessPrompt"], fixture["input"]) == golden


def test_scoring_rule_version_matches_corpus():
    assert CORPUS["scoringRuleVersion"] == contracts.SCORING_RULE_VERSION


@pytest.mark.parametrize("case", CORPUS["cases"], ids=[c["name"] for c in CORPUS["cases"]])
def test_scoring_matches_shared_corpus(case):
    scored = contracts.score(case["dimensionScores"], case["confidence"], case["dimensions"],
                             Decimal(case["passThreshold"]), Decimal(case["rejectThreshold"]))

    assert scored.final_score == Decimal(case["expectedFinalScore"])
    assert scored.recommendation == case["expectedRecommendation"]


def test_tool_definition_is_the_record_ai_review_function():
    tool = contracts.tool_definition()

    assert tool["name"] == "record_ai_review"
    assert tool["parameters"]["additionalProperties"] is False
