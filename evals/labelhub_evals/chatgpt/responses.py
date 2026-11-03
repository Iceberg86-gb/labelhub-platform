"""Streaming Responses API client and the `chatgpt` eval provider (ADR-018 §2, §5).

POST {api_base}/responses with store=false and stream=true, parsed as server-sent events. A response
counts as successful only after `response.completed`.
"""

from __future__ import annotations

import json
import time
from dataclasses import dataclass, field
from typing import Callable

from labelhub_evals import review
from labelhub_evals.chatgpt.http import Http, HttpError
from labelhub_evals.chatgpt.session import ChatGPTSession
from labelhub_evals.providers.base import ProviderError, ProviderResponse

USAGE_LIMIT_CODES = {"subscription_sharing_usage_limit_exceeded", "subscription_sharing_usage_unavailable"}


@dataclass
class StreamResult:
    text: str = ""
    function_calls: list[dict] = field(default_factory=list)
    usage: dict = field(default_factory=dict)
    completed: bool = False


def iter_sse(lines) -> "list[dict]":
    data: list[str] = []
    for line in lines:
        if line == "":
            if data:
                payload = "\n".join(data)
                data = []
                if payload.strip() == "[DONE]":
                    return
                yield json.loads(payload)
            continue
        if line.startswith(":"):
            continue
        if line.startswith("data:"):
            data.append(line[5:].lstrip(" "))
    if data:
        yield json.loads("\n".join(data))


def consume(events, on_delta: Callable[[str], None] | None = None) -> StreamResult:
    result = StreamResult()
    for event in events:
        kind = event.get("type")
        if kind == "response.output_text.delta":
            delta = event.get("delta") or ""
            result.text += delta
            if on_delta:
                on_delta(delta)
        elif kind == "response.output_item.done":
            item = event.get("item") or {}
            if item.get("type") == "function_call":
                result.function_calls.append(item)
        elif kind == "response.completed":
            response = event.get("response") or {}
            result.usage = response.get("usage") or {}
            if not result.function_calls:
                result.function_calls = [i for i in response.get("output") or [] if isinstance(i, dict) and i.get("type") == "function_call"]
            result.completed = True
        elif kind in ("response.failed", "response.incomplete", "error"):
            error = (event.get("response") or {}).get("error") or event.get("error") or event
            code = error.get("code") if isinstance(error, dict) else None
            if code in USAGE_LIMIT_CODES:
                raise ProviderError(f"ChatGPT plan usage limit reached ({code}); wait for the window to reset or replay recordings")
            message = error.get("message") if isinstance(error, dict) else None
            raise ProviderError(f"response {kind}: {code or ''} {message or ''}".strip())
    if not result.completed:
        raise ProviderError("stream ended before response.completed")
    return result


class ResponsesClient:
    def __init__(self, session: ChatGPTSession, http: Http):
        self.session, self.http = session, http

    def stream(self, body: dict, on_delta: Callable[[str], None] | None = None) -> StreamResult:
        body = dict(body, store=False, stream=True)
        url = f"{self.session.endpoints.api_base}/responses"

        def attempt(token: str) -> StreamResult:
            lines = self.http.stream_lines("POST", url, headers={"Authorization": f"Bearer {token}", "Accept": "text/event-stream"}, json_body=body)
            return consume(iter_sse(lines), on_delta)

        try:
            return self.session.call(attempt)
        except HttpError as exc:
            detail = exc.json().get("error")
            code = detail.get("code") if isinstance(detail, dict) else None
            if code in USAGE_LIMIT_CODES:
                raise ProviderError(f"ChatGPT plan usage limit reached ({code})") from None
            raise ProviderError(f"Responses API error: {exc}") from None


def review_body(model: str, effort: str | None, review_input: dict) -> dict:
    body = {
        "model": model,
        "instructions": review.SYSTEM_INSTRUCTIONS,
        "input": [{"role": "user", "content": [{"type": "input_text", "text": review.user_message(review_input)}]}],
        "tools": [{"type": "function", "name": review.TOOL_NAME, "description": review.TOOL_DESCRIPTION, "parameters": review.tool_parameters()}],
        "tool_choice": {"type": "function", "name": review.TOOL_NAME},
        "parallel_tool_calls": False,
    }
    if effort:
        body["reasoning"] = {"effort": effort}
    return body


def correction_body(model: str, effort: str | None, review_input: dict, attempts: list, style: str) -> dict:
    """ADR-019 correction turn for the Responses API (store=false, so the conversation is sent inline).
    tool style: each failed call is replayed as a function_call item followed by its function_call_output.
    text style: one user turn quoting the previous calls (fallback if a model rejects replayed calls)."""
    from labelhub_evals.review_loop import text_style_message

    body = review_body(model, effort, review_input)
    if style == "text":
        body["input"] = [{"role": "user", "content": [{"type": "input_text", "text": text_style_message(review_input, attempts)}]}]
        return body
    for i, attempt in enumerate(attempts, 1):
        call_id = f"call_review_attempt_{i}"
        body["input"].append({"type": "function_call", "call_id": call_id, "name": review.TOOL_NAME, "arguments": attempt.arguments})
        body["input"].append({"type": "function_call_output", "call_id": call_id, "output": attempt.feedback})
    return body


class ChatGPTProvider:
    def __init__(self, client: ResponsesClient, model: str, effort: str | None):
        self.client, self.model, self.effort = client, model, effort

    @property
    def identity(self) -> str:
        return f"chatgpt/{self.model}" + (f"@{self.effort}" if self.effort else "")

    def review(self, review_input: dict) -> ProviderResponse:
        return self._call(review_body(self.model, self.effort, review_input))

    def correct(self, review_input: dict, attempts: list, style: str) -> ProviderResponse:
        return self._call(correction_body(self.model, self.effort, review_input, attempts, style))

    def _call(self, body: dict) -> ProviderResponse:
        start = time.monotonic()
        result = self.client.stream(body)
        latency_ms = int((time.monotonic() - start) * 1000)
        tokens = (result.usage.get("input_tokens"), result.usage.get("output_tokens"))
        calls = [c for c in result.function_calls if c.get("name") == review.TOOL_NAME]
        if not calls:
            return ProviderResponse(None, "model answered without calling record_ai_review", *tokens, latency_ms)
        return ProviderResponse(calls[0].get("arguments"), None, *tokens, latency_ms)

    def ask(self, prompt: str, on_delta: Callable[[str], None]) -> StreamResult:
        body = {"model": self.model, "instructions": "You are a concise assistant.", "input": [{"role": "user", "content": [{"type": "input_text", "text": prompt}]}]}
        if self.effort:
            body["reasoning"] = {"effort": self.effort}
        return self.client.stream(body, on_delta)
