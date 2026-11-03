"""Record / replay store (ADR-017 §3).

Key = SHA-256 of canonical {model, prompt_version, input_hash}. One JSON file per key, sharded by the
first two hex chars, so recordings merge cleanly in git. Recordings are append-only: an existing
recording is never overwritten (a re-record with a different answer is an error, not an update).
"""

from __future__ import annotations

import json
from pathlib import Path

from labelhub_evals.canonical import canonical_hash, canonical_json
from labelhub_evals.providers.base import Provider, ProviderResponse

MODES = ("replay", "record", "live")


class ReplayMiss(LookupError):
    pass


def recording_key(model: str, prompt_version: str, input_hash: str) -> str:
    return canonical_hash({"input_hash": input_hash, "model": model, "prompt_version": prompt_version})


class RecordingStore:
    def __init__(self, root: Path):
        self.root = root

    def path(self, key: str) -> Path:
        return self.root / key[:2] / f"{key}.json"

    def get(self, key: str) -> ProviderResponse | None:
        path = self.path(key)
        if not path.exists():
            return None
        return ProviderResponse.from_json(json.loads(path.read_text(encoding="utf-8"))["response"])

    def put(self, key: str, *, model: str, prompt_version: str, input_hash: str, response: ProviderResponse) -> None:
        path = self.path(key)
        if path.exists():
            raise FileExistsError(f"recording {key} already exists; recordings are append-only")
        path.parent.mkdir(parents=True, exist_ok=True)
        record = {"input_hash": input_hash, "key": key, "model": model, "prompt_version": prompt_version, "response": response.to_json()}
        tmp = path.with_suffix(".part")
        tmp.write_text(canonical_json(record) + "\n", encoding="utf-8")
        tmp.replace(path)


class RecordingProvider:
    """replay: cache only, a miss is an error. record: cache first, call the model on a miss and save.
    live: always call the model, never save (for ad-hoc checks)."""

    def __init__(self, inner: Provider | None, store: RecordingStore, mode: str, model: str, prompt_version: str):
        if mode not in MODES:
            raise ValueError(f"mode must be one of {MODES}")
        if mode != "replay" and inner is None:
            raise ValueError(f"mode {mode} needs a live provider")
        self.inner, self.store, self.mode, self.model, self.prompt_version = inner, store, mode, model, prompt_version
        self.hits = 0
        self.misses = 0

    def review(self, input_hash: str, review_input: dict) -> ProviderResponse:
        key = recording_key(self.model, self.prompt_version, input_hash)
        if self.mode != "live":
            cached = self.store.get(key)
            if cached is not None:
                self.hits += 1
                return cached
            if self.mode == "replay":
                raise ReplayMiss(f"no recording for {self.model} / {self.prompt_version} / {input_hash}")
        self.misses += 1
        response = self.inner.review(review_input)
        if self.mode == "record":
            self.store.put(key, model=self.model, prompt_version=self.prompt_version, input_hash=input_hash, response=response)
        return response

    def correct(self, input_hash: str, review_input: dict, attempts: list, style: str) -> ProviderResponse:
        """ADR-019 correction turn. Keyed by the whole conversation so far (each attempt's arguments and
        feedback), under a prompt version that names the loop and the correction style."""
        from labelhub_evals.review_loop import FEEDBACK_VERSION

        prompt_version = f"{self.prompt_version}+{FEEDBACK_VERSION}" + ("" if style == "tool" else f"+{style}")
        turn_hash = canonical_hash({"attempts": [{"arguments": a.arguments, "feedback": a.feedback} for a in attempts], "input_hash": input_hash})
        key = recording_key(self.model, prompt_version, turn_hash)
        if self.mode != "live":
            cached = self.store.get(key)
            if cached is not None:
                self.hits += 1
                return cached
            if self.mode == "replay":
                raise ReplayMiss(f"no recording for correction turn {len(attempts)} of {input_hash} ({self.model} / {prompt_version})")
        self.misses += 1
        response = self.inner.correct(review_input, attempts, style)
        if self.mode == "record":
            self.store.put(key, model=self.model, prompt_version=prompt_version, input_hash=turn_hash, response=response)
        return response
