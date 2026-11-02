"""Chat-completions providers: live OpenAI-compatible HTTP, a deterministic fake for tests, and record/replay.

Replay is what makes a run reproducible and free: responses are stored by a hash of the exact request body, so a
replayed run is byte-identical to the recorded one, and a request that was never recorded fails loudly.
"""

from __future__ import annotations

import json
import os
import random
import threading
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Protocol

from labelhub_evals.canonical import canonical_json, sha256_json


@dataclass
class Completion:
    response: dict[str, Any]
    latency_ms: int
    usage: dict[str, int]


class ProviderError(Exception):
    def __init__(self, code: str, retryable: bool):
        super().__init__(code)
        self.code = code
        self.retryable = retryable


class Provider(Protocol):
    name: str

    def complete(self, request: dict[str, Any]) -> Completion: ...


def request_key(request: dict[str, Any]) -> str:
    return sha256_json(request)


def _usage(response: dict[str, Any]) -> dict[str, int]:
    usage = response.get("usage") or {}
    cached = usage.get("prompt_cache_hit_tokens")
    if cached is None:
        cached = (usage.get("prompt_tokens_details") or {}).get("cached_tokens", 0)
    return {
        "prompt_tokens": int(usage.get("prompt_tokens") or 0),
        "completion_tokens": int(usage.get("completion_tokens") or 0),
        "cached_prompt_tokens": int(cached or 0),
    }


class OpenAICompatibleProvider:
    """POST {base_url}/chat/completions with retries, like the agent's runtime client (30s timeout, 3 attempts)."""

    def __init__(self, base_url: str, api_key: str, timeout_s: float = 30, max_attempts: int = 3, base_delay_s: float = 1):
        if not api_key:
            raise ValueError("an API key is required for live calls (set LABELHUB_EVAL_API_KEY)")
        self.name = "openai-compatible"
        self._url = base_url.rstrip("/") + "/chat/completions"
        self._key = api_key
        self._timeout = timeout_s
        self._attempts = max_attempts
        self._delay = base_delay_s

    def _once(self, request: dict[str, Any]) -> Completion:
        body = json.dumps(request, ensure_ascii=False).encode("utf-8")
        http = urllib.request.Request(
            self._url, data=body, method="POST",
            headers={"Authorization": f"Bearer {self._key}", "Content-Type": "application/json"},
        )
        started = time.monotonic()
        try:
            with urllib.request.urlopen(http, timeout=self._timeout) as reply:
                payload = json.loads(reply.read().decode("utf-8"))
        except urllib.error.HTTPError as error:
            raise ProviderError(f"http_{error.code}", retryable=error.code == 429 or error.code >= 500) from None
        except (urllib.error.URLError, TimeoutError, OSError):
            raise ProviderError("network_error", retryable=True) from None
        except json.JSONDecodeError:
            raise ProviderError("invalid_json", retryable=False) from None
        return Completion(payload, int((time.monotonic() - started) * 1000), _usage(payload))

    def complete(self, request: dict[str, Any]) -> Completion:
        for attempt in range(1, self._attempts + 1):
            try:
                return self._once(request)
            except ProviderError as error:
                if not error.retryable or attempt == self._attempts:
                    raise
                time.sleep(self._delay * 2 ** (attempt - 1))
        raise AssertionError("unreachable")


class FakeProvider:
    """Deterministic pseudo-reviews keyed by the request hash. Useful for tests and wiring checks, meaningless as a
    model: its scores are noise."""

    name = "fake"

    def complete(self, request: dict[str, Any]) -> Completion:
        rng = random.Random(request_key(request))
        dims = [{"dimension": d, "score": round(rng.random(), 2), "reason": "fake"} for d in ("helpfulness", "correctness")]
        args = {"overallSuggestion": "manual_review", "confidence": round(rng.random(), 2), "summary": "fake",
                "dimensionScores": dims, "fieldFindings": []}
        response = {"choices": [{"message": {"tool_calls": [{"type": "function", "function": {
            "name": "record_ai_review", "arguments": json.dumps(args)}}]}}],
            "usage": {"prompt_tokens": 1000 + rng.randint(0, 500), "completion_tokens": 80, "prompt_cache_hit_tokens": 0}}
        return Completion(response, 100 + rng.randint(0, 900), _usage(response))


class RecordingProvider:
    """record: call the inner provider and append each exchange to a cassette (JSONL).
    replay: answer only from cassettes; a miss raises, so a replayed run can never silently hit the network."""

    def __init__(self, mode: str, cassette_dir: Path, cassette_name: str, inner: Provider | None = None):
        if mode not in ("record", "replay"):
            raise ValueError(mode)
        if mode == "record" and inner is None:
            raise ValueError("record mode needs an inner provider")
        self.name = f"{mode}:{inner.name if inner else 'cassette'}"
        self.mode = mode
        self._inner = inner
        self._lock = threading.Lock()
        self._cassette = cassette_dir / f"{cassette_name}.jsonl"
        self._store: dict[str, dict[str, Any]] = {}
        for path in sorted(cassette_dir.glob("*.jsonl")) if cassette_dir.exists() else []:
            for line in path.read_text(encoding="utf-8").splitlines():
                if line.strip():
                    entry = json.loads(line)
                    self._store[entry["key"]] = entry

    def complete(self, request: dict[str, Any]) -> Completion:
        key = request_key(request)
        hit = self._store.get(key)
        if hit is not None:
            return Completion(hit["response"], hit["latencyMs"], hit["usage"])
        if self.mode == "replay":
            raise ProviderError("replay_miss", retryable=False)
        completion = self._inner.complete(request)
        entry = {"key": key, "model": request.get("model"), "response": completion.response,
                 "latencyMs": completion.latency_ms, "usage": completion.usage}
        with self._lock:
            self._store[key] = entry
            self._cassette.parent.mkdir(parents=True, exist_ok=True)
            with open(self._cassette, "a", encoding="utf-8") as handle:
                handle.write(canonical_json(entry) + "\n")
        return completion


def from_args(kind: str, cassette_dir: Path, cassette_name: str, base_url: str | None) -> Provider:
    if kind == "fake":
        return FakeProvider()
    if kind == "replay":
        return RecordingProvider("replay", cassette_dir, cassette_name)
    live = OpenAICompatibleProvider(base_url or os.environ.get("LABELHUB_EVAL_BASE_URL", "https://api.deepseek.com"),
                                    os.environ.get("LABELHUB_EVAL_API_KEY", ""))
    if kind == "live":
        return live
    if kind == "record":
        return RecordingProvider("record", cassette_dir, cassette_name, live)
    raise ValueError(f"unknown provider {kind!r}")
