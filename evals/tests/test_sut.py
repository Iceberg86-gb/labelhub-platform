import json
from decimal import Decimal

from labelhub_evals.datasets.helpsteer2 import Case
from labelhub_evals.sut import ReviewRule, build_request, parse_response

RULE = ReviewRule("helpsteer2-review.v1")
CASE = Case("hs2-test", "What is 2+2?", "It is 4.", "accept", {"helpfulness": 4, "correctness": 4})


def _tool_response(args):
    return {"choices": [{"message": {"tool_calls": [{"function": {"name": "record_ai_review", "arguments": json.dumps(args)}}]}}]}


def test_request_mirrors_the_production_agent_request():
    request = build_request(CASE, RULE, "deepseek-v4-flash")

    assert request["temperature"] == 0
    assert request["tool_choice"] == {"type": "function", "function": {"name": "record_ai_review"}}
    assert len(request["messages"]) == 1 and request["messages"][0]["role"] == "user"
    content = request["messages"][0]["content"]
    assert "Business prompt:\n" + RULE.business_prompt in content
    assert '"answerPayload":{"response":"It is 4."}' in content
    assert '"datasetItemPayload":{"prompt":"What is 2+2?"}' in content


def test_scores_route_through_the_three_zone_policy():
    review = parse_response(_tool_response({"dimensionScores": [
        {"dimension": "helpfulness", "score": 0.9}, {"dimension": "correctness", "score": 0.8}], "confidence": 0.1}), RULE)

    assert review.route == "pass"
    assert review.final_score == Decimal("0.8500")
    assert review.error is None and review.warnings == []


def test_missing_dimension_falls_back_to_confidence_with_a_warning():
    review = parse_response(_tool_response({"dimensionScores": [{"dimension": "helpfulness", "score": 1.0}],
                                            "confidence": 0.2}), RULE)

    assert review.final_score == Decimal("0.6000")
    assert review.warnings == ["dimension_fallback:correctness"]


def test_malformed_outputs_fail_to_human_never_to_auto_pass():
    no_tool = {"choices": [{"message": {"content": "looks fine, pass"}}]}
    wrong_fn = {"choices": [{"message": {"tool_calls": [{"function": {"name": "other", "arguments": "{}"}}]}}]}
    bad_json = {"choices": [{"message": {"tool_calls": [{"function": {"name": "record_ai_review", "arguments": "{"}}]}}]}

    for response, code in ((no_tool, "missing_tool_call"), (wrong_fn, "unexpected_function"),
                           (bad_json, "invalid_arguments_json"), ({}, "malformed_response")):
        review = parse_response(response, RULE)
        assert (review.route, review.error) == ("manual_review", code)
