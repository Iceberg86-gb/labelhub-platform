"""HelpSteer2 golden set (ADR-017 §3).

The source file is pinned by Hugging Face revision and SHA-256. Sampling uses a fixed seed, so the
golden set is a pure function of (source bytes, seed, size, label rule). Only manifest.json and the
small smoke subset are committed; `golden build` rebuilds golden.jsonl and fails if its hash differs
from the manifest.

HelpSteer2 is CC-BY-4.0 (NVIDIA). Attribution lives in datasets/helpsteer2-h1/NOTICE.md.
"""

from __future__ import annotations

import gzip
import hashlib
import json
import random
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

from labelhub_evals.canonical import canonical_hash, canonical_json

SOURCE_DATASET = "nvidia/HelpSteer2"
SOURCE_REVISION = "990b2711a36180dd19d9c94b8627844866f8982a"
SOURCE_FILE = "validation.jsonl.gz"
SOURCE_SHA256 = "610eeb5289494d613c4c0f70aade2df8df0b499f3a24e76d232f74e6909d010a"
SOURCE_URL = f"https://huggingface.co/datasets/{SOURCE_DATASET}/resolve/{SOURCE_REVISION}/{SOURCE_FILE}"

DEFAULT_SEED = 20261102
# humanpending #265 (264-2): use every validation row. size=None means "all rows, source order";
# a size subsamples with the seed (kept for quick local experiments).
DEFAULT_SIZE: int | None = None
SMOKE_SIZE = 24

# Provisional truth mapping, recorded in docs/process/humanpending.md (#264) until the owner confirms.
LABEL_RULE_VERSION = "hs2-pass-v1"
LABEL_RULE = "pass iff helpfulness >= 3 and correctness >= 3 (0-4 scale); otherwise reject"


def truth_label(row: dict) -> str:
    return "pass" if row["helpfulness"] >= 3 and row["correctness"] >= 3 else "reject"


@dataclass(frozen=True)
class GoldenItem:
    item_id: str  # = input_hash: canonical SHA-256 of the review input
    source_index: int
    input: dict
    truth: str
    attributes: dict

    def to_json(self) -> dict:
        return {
            "item_id": self.item_id,
            "source_index": self.source_index,
            "input": self.input,
            "truth": self.truth,
            "attributes": self.attributes,
        }

    @staticmethod
    def from_json(data: dict) -> "GoldenItem":
        return GoldenItem(data["item_id"], data["source_index"], data["input"], data["truth"], data["attributes"])


def review_input(row: dict) -> dict:
    return {"prompt": row["prompt"], "response": row["response"]}


def fetch_file(cache_dir: Path, file: str, sha256: str) -> Path:
    """Download one file of the pinned HelpSteer2 revision into the cache and verify its SHA-256."""
    cache_dir.mkdir(parents=True, exist_ok=True)
    target = cache_dir / f"helpsteer2-{SOURCE_REVISION[:12]}-{file}"
    if not target.exists():
        tmp = target.with_suffix(".part")
        url = f"https://huggingface.co/datasets/{SOURCE_DATASET}/resolve/{SOURCE_REVISION}/{file}"
        with urllib.request.urlopen(url, timeout=120) as response, open(tmp, "wb") as out:
            out.write(response.read())
        tmp.replace(target)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    if digest != sha256:
        raise ValueError(f"{target} sha256 {digest} does not match pinned {sha256}")
    return target


def fetch_source(cache_dir: Path) -> Path:
    return fetch_file(cache_dir, SOURCE_FILE, SOURCE_SHA256)


def read_source(path: Path) -> list[dict]:
    with gzip.open(path, "rt", encoding="utf-8") as handle:
        return [json.loads(line) for line in handle if line.strip()]


def sampling_method(size: int | None) -> str:
    return "all rows, source order" if size is None else "random.Random(seed).sample, sorted by source index"


def build_items(rows: list[dict], *, seed: int = DEFAULT_SEED, size: int | None = DEFAULT_SIZE) -> list[GoldenItem]:
    if size is None:
        indices = list(range(len(rows)))
    else:
        if size > len(rows):
            raise ValueError(f"size {size} exceeds source rows {len(rows)}")
        indices = sorted(random.Random(seed).sample(range(len(rows)), size))
    items: list[GoldenItem] = []
    seen: set[str] = set()
    for index in indices:
        row = rows[index]
        payload = review_input(row)
        item_id = canonical_hash(payload)
        if item_id in seen:  # identical prompt/response pairs would collide in the replay cache
            continue
        seen.add(item_id)
        attributes = {k: row[k] for k in ("helpfulness", "correctness", "coherence", "complexity", "verbosity")}
        items.append(GoldenItem(item_id, index, payload, truth_label(row), attributes))
    return items


def jsonl_bytes(items: Iterable[GoldenItem]) -> bytes:
    return "".join(canonical_json(item.to_json()) + "\n" for item in items).encode("utf-8")


def manifest_for(items: list[GoldenItem], golden_bytes: bytes, smoke_bytes: bytes, *, seed: int, size: int | None) -> dict:
    counts = {"pass": 0, "reject": 0}
    for item in items:
        counts[item.truth] += 1
    return {
        "name": "helpsteer2-h1",
        "license": "CC-BY-4.0",
        "source": {
            "dataset": SOURCE_DATASET,
            "revision": SOURCE_REVISION,
            "file": SOURCE_FILE,
            "sha256": SOURCE_SHA256,
        },
        "sampling": {"method": sampling_method(size)} if size is None else {"method": sampling_method(size), "requested_size": size, "seed": seed},
        "label_rule": {"version": LABEL_RULE_VERSION, "rule": LABEL_RULE},
        "size": len(items),
        "label_counts": counts,
        "golden_sha256": hashlib.sha256(golden_bytes).hexdigest(),
        "smoke": {"size": min(SMOKE_SIZE, len(items)), "sha256": hashlib.sha256(smoke_bytes).hexdigest()},
    }


def build(out_dir: Path, cache_dir: Path, *, seed: int = DEFAULT_SEED, size: int | None = DEFAULT_SIZE, check: bool = True) -> dict:
    rows = read_source(fetch_source(cache_dir))
    items = build_items(rows, seed=seed, size=size)
    golden_bytes = jsonl_bytes(items)
    smoke_bytes = jsonl_bytes(items[:SMOKE_SIZE])
    manifest = manifest_for(items, golden_bytes, smoke_bytes, seed=seed, size=size)
    out_dir.mkdir(parents=True, exist_ok=True)
    manifest_path = out_dir / "manifest.json"
    if check and manifest_path.exists():
        pinned = json.loads(manifest_path.read_text(encoding="utf-8"))
        if pinned != manifest:
            raise ValueError("rebuilt golden set does not match the committed manifest.json; refusing to overwrite")
    (out_dir / "golden.jsonl").write_bytes(golden_bytes)
    (out_dir / "smoke.jsonl").write_bytes(smoke_bytes)
    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False, sort_keys=True) + "\n", encoding="utf-8")
    return manifest


def load(path: Path) -> list[GoldenItem]:
    with open(path, encoding="utf-8") as handle:
        return [GoldenItem.from_json(json.loads(line)) for line in handle if line.strip()]
