"""Deterministic offline provider. It exists to exercise the pipeline (CI, tests), not to be good."""

from __future__ import annotations

import json

from labelhub_evals.canonical import canonical_hash
from labelhub_evals.providers.base import ProviderResponse


class FakeProvider:
    identity = "fake/hash-heuristic-v1"

    def review(self, review_input: dict) -> ProviderResponse:
        digest = canonical_hash(review_input)
        bucket = int(digest[:8], 16) % 1000
        if bucket < 30:  # ~3% simulated format violations, so the metric path is exercised
            return ProviderResponse(None, "model answered with text instead of record_ai_review", 120, 40, 900)
        response = review_input.get("response", "")
        # Weak signal: longer answers score a little higher; hash adds deterministic noise.
        score = min(1.0, 0.35 + min(len(response), 2000) / 4000 + (bucket % 100) / 250)
        score = round(score, 4)
        suggestion = "pass" if score >= 0.8 else "reject" if score <= 0.2 else "manual_review"
        arguments = {
            "overallSuggestion": suggestion,
            "confidence": score,
            "summary": "fake provider heuristic",
            "dimensionScores": [{"dimension": "overall", "score": score, "reason": "length/hash heuristic"}],
            "fieldFindings": [],
        }
        tokens_in = 200 + len(json.dumps(review_input)) // 4
        return ProviderResponse(json.dumps(arguments, sort_keys=True), None, tokens_in, 60, 400 + bucket)
