"""Account model catalog + Auto latest selection (ADR-018 §3).

The catalog is parsed defensively: only `slug` (or `id`) is required. Reasoning efforts, priority
and minimum client version are used when present. New models in the catalog become selectable
without code changes.
"""

from __future__ import annotations

import re
from dataclasses import dataclass



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


def _entries(payload) -> list:
    if isinstance(payload, list):
        return payload
    if not isinstance(payload, dict):
        raise ValueError("model catalog is not a JSON object or array")
    for key in ("data", "models", "items"):
        if isinstance(payload.get(key), list):
            return payload[key]
    raise ValueError(f"model catalog has no data/models list (top-level keys: {', '.join(sorted(payload)) or 'none'})")


def _slug(entry: dict) -> str | None:
    for key in ("slug", "id", "model", "name"):
        value = entry.get(key)
        if isinstance(value, str) and value:
            return value
    return None


def _listed(entry: dict) -> bool:
    visibility = entry.get("visibility")
    return visibility is None or str(visibility).lower() == "list"


def diagnose_catalog(payload) -> str:
    """Why parse_catalog kept nothing: shape and per-entry drop reasons, no secrets involved."""
    try:
        entries = _entries(payload)
    except ValueError as exc:
        return str(exc)
    from collections import Counter

    visibility = Counter(str(e.get("visibility")) for e in entries if isinstance(e, dict) and not _listed(e))
    no_slug = sum(1 for e in entries if isinstance(e, dict) and _listed(e) and _slug(e) is None)
    keys = sorted({k for e in entries if isinstance(e, dict) for k in e})
    return (f"{len(entries)} catalog entries; dropped as not visibility=list: {dict(visibility) or 0}; "
            f"no slug/id: {no_slug}; entry keys seen: {', '.join(keys) or 'none'}")


def parse_catalog(payload) -> list[ModelInfo]:
    models: list[ModelInfo] = []
    for entry in _entries(payload):
        if not isinstance(entry, dict) or not _listed(entry):
            continue
        slug = _slug(entry)
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


def client_supported(model: ModelInfo, client_version: str | None = None) -> bool:
    # minimal_client_version is on the Codex CLI's version scale, not ours, so it only filters when a
    # caller passes a version on that scale explicitly.
    return client_version is None or model.minimal_client_version is None or version_tuple(model.minimal_client_version) <= version_tuple(client_version)


def ranked(models: list[ModelInfo], client_version: str | None = None) -> list[ModelInfo]:
    """Recommended first: lower priority number first (missing priority last), then newer version
    parsed from the slug, then slug for a total, deterministic order."""
    usable = [m for m in models if client_supported(m, client_version)]
    return sorted(usable, key=lambda m: (m.priority if m.priority is not None else 1 << 30, tuple(-v for v in version_tuple(m.slug)), m.slug))


def pick_latest(models: list[ModelInfo], client_version: str | None = None) -> ModelInfo:
    order = ranked(models, client_version)
    if not order:
        raise ValueError("no usable model in the account catalog; run `chatgpt models` to see why, "
                         "or pick one explicitly with `chatgpt select MODEL --skip-catalog`")
    return order[0]


def fetch_catalog_raw(session, http, endpoints):
    # Plain GET /v1/models as in OpenAI's sign-in docs. No client_version query: that came from the
    # Codex backend, and a low value made the server return an empty catalog on a real account.
    url = f"{endpoints.api_base}/models"
    return session.call(lambda token: http.json("GET", url, headers={"Authorization": f"Bearer {token}"}))


def fetch_catalog(session, http, endpoints) -> list[ModelInfo]:
    return parse_catalog(fetch_catalog_raw(session, http, endpoints))
