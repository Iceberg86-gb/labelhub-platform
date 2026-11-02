"""Account model catalog + Auto latest selection (ADR-018 §3).

The catalog is parsed defensively: only `slug` (or `id`) is required. Reasoning efforts, priority
and minimum client version are used when present. New models in the catalog become selectable
without code changes.
"""

from __future__ import annotations

import re
from dataclasses import dataclass

from labelhub_evals import CLIENT_VERSION


@dataclass(frozen=True)
class ModelInfo:
    slug: str
    display_name: str
    reasoning_efforts: tuple[str, ...]
    default_effort: str | None
    priority: int | None
    minimal_client_version: str | None

    def supports_effort(self, effort: str | None) -> bool:
        return effort is None or not self.reasoning_efforts or effort in self.reasoning_efforts


def _efforts(entry: dict) -> tuple[str, ...]:
    for key in ("supported_reasoning_levels", "supported_reasoning_efforts", "reasoning_efforts"):
        value = entry.get(key)
        if isinstance(value, list):
            out = []
            for v in value:
                if isinstance(v, str):
                    out.append(v)
                elif isinstance(v, dict) and isinstance(v.get("effort"), str):
                    out.append(v["effort"])
            return tuple(out)
    return ()


def parse_catalog(payload: dict) -> list[ModelInfo]:
    entries = payload.get("data") if isinstance(payload.get("data"), list) else payload.get("models")
    if not isinstance(entries, list):
        raise ValueError("model catalog has neither `data` nor `models` list")
    models: list[ModelInfo] = []
    for entry in entries:
        if not isinstance(entry, dict):
            continue
        if "visibility" in entry and entry["visibility"] != "list":
            continue
        slug = entry.get("slug") or entry.get("id")
        if not isinstance(slug, str) or not slug:
            continue
        priority = entry.get("priority")
        models.append(ModelInfo(
            slug=slug,
            display_name=str(entry.get("display_name") or slug),
            reasoning_efforts=_efforts(entry),
            default_effort=entry.get("default_reasoning_level") or entry.get("default_reasoning_effort"),
            priority=priority if isinstance(priority, int) and not isinstance(priority, bool) else None,
            minimal_client_version=entry.get("minimal_client_version") or entry.get("min_client_version"),
        ))
    return models


def version_tuple(text: str | None, width: int = 4) -> tuple[int, ...]:
    if not text:
        return (0,) * width
    match = re.search(r"\d+(?:\.\d+)*", text)
    parts = [int(p) for p in match.group(0).split(".")] if match else []
    return tuple((parts + [0] * width)[:width])


def client_supported(model: ModelInfo, client_version: str = CLIENT_VERSION) -> bool:
    return model.minimal_client_version is None or version_tuple(model.minimal_client_version) <= version_tuple(client_version)


def ranked(models: list[ModelInfo], client_version: str = CLIENT_VERSION) -> list[ModelInfo]:
    """Recommended first: lower priority number first (missing priority last), then newer version
    parsed from the slug, then slug for a total, deterministic order."""
    usable = [m for m in models if client_supported(m, client_version)]
    return sorted(usable, key=lambda m: (m.priority if m.priority is not None else 1 << 30, tuple(-v for v in version_tuple(m.slug)), m.slug))


def pick_latest(models: list[ModelInfo], client_version: str = CLIENT_VERSION) -> ModelInfo:
    order = ranked(models, client_version)
    if not order:
        raise ValueError("no model in the account catalog is usable by this client version")
    return order[0]


def fetch_catalog(session, http, endpoints) -> list[ModelInfo]:
    url = f"{endpoints.api_base}/models?client_version={CLIENT_VERSION}"
    payload = session.call(lambda token: http.json("GET", url, headers={"Authorization": f"Bearer {token}"}))
    return parse_catalog(payload)
