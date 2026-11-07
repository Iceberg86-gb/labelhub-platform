"""OpenAI-compatible chat-completions provider (API key). Same request shape as the production
services/agent OpenAiCompatibleAiReviewRuntimeClient, so H1 numbers transfer to production."""

from __future__ import annotations

import json
import time
import urllib.error
import urllib.request

from labelhub_evals import review
from labelhub_evals.providers.base import ProviderError, ProviderResponse


class OpenAiCompatibleProvider:
    def __init__(self, base_url: str, api_key: str, model: str, timeout: float = 60.0):
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self.model = model
        self.timeout = timeout

    @property
    def identity(self) -> str:
        return f"openai-compatible/{self.model}"

    def request_body(self, review_input: dict, precedent_block: str | None = None) -> dict:
        return {
            "model": self.model,
            "messages": [
                {"role": "system", "content": review.SYSTEM_INSTRUCTIONS},
                {"role": "user", "content": review.user_message(review_input, precedent_block)},
            ],
            "temperature": 0,
            "tools": [{"type": "function", "function": {"name": review.TOOL_NAME, "description": review.TOOL_DESCRIPTION, "parameters": review.tool_parameters()}}],
            "tool_choice": {"type": "function", "function": {"name": review.TOOL_NAME}},
        }

    def correction_body(self, review_input: dict, attempts: list, style: str, precedent_block: str | None = None) -> dict:
        """Same conversation shape as services/agent OpenAiCompatibleAiReviewRuntimeClient."""
        from labelhub_evals.review_loop import text_style_message

        body = self.request_body(review_input, precedent_block)
        if style == "text":
            body["messages"][1]["content"] = text_style_message(review_input, attempts, precedent_block)
            return body
        for i, attempt in enumerate(attempts, 1):
            call_id = f"call_review_attempt_{i}"
            body["messages"].append({"role": "assistant", "content": None, "tool_calls": [
                {"id": call_id, "type": "function", "function": {"name": review.TOOL_NAME, "arguments": attempt.arguments}}]})
            body["messages"].append({"role": "tool", "tool_call_id": call_id, "content": attempt.feedback})
        return body

    def correct(self, review_input: dict, attempts: list, style: str, precedent_block: str | None = None) -> ProviderResponse:
        return self._send(self.correction_body(review_input, attempts, style, precedent_block))

    def review(self, review_input: dict, precedent_block: str | None = None) -> ProviderResponse:
        return self._send(self.request_body(review_input, precedent_block))

    def _send(self, body: dict) -> ProviderResponse:
        request = urllib.request.Request(
            self.base_url + "/chat/completions",
            data=json.dumps(body).encode("utf-8"),
            headers={"Authorization": f"Bearer {self.api_key}", "Content-Type": "application/json"},
            method="POST",
        )
        start = time.monotonic()
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                body = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            raise ProviderError(f"provider returned HTTP {exc.code}") from exc
        except (urllib.error.URLError, TimeoutError) as exc:
            raise ProviderError(f"provider request failed: {exc}") from exc
        latency_ms = int((time.monotonic() - start) * 1000)
        usage = body.get("usage") or {}
        tokens = (usage.get("prompt_tokens"), usage.get("completion_tokens"))
        try:
            call = body["choices"][0]["message"]["tool_calls"][0]["function"]
        except (KeyError, IndexError, TypeError):
            return ProviderResponse(None, "response has no tool call", *tokens, latency_ms)
        if call.get("name") != review.TOOL_NAME:
            return ProviderResponse(None, f"unexpected function {call.get('name')!r}", *tokens, latency_ms)
        return ProviderResponse(call.get("arguments"), None, *tokens, latency_ms)
