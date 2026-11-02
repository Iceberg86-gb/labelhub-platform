"""HelpSteer2 (nvidia/HelpSteer2, CC-BY-4.0) as an eval set for response-quality review.

Each row is one labeling submission: the dataset item is the prompt, the labeler's answer is the response, and the
human 0-4 attribute scores give the ground truth. The download is pinned to a revision and verified by sha256.
"""

from __future__ import annotations

import gzip
import json
import random
import urllib.request
from collections.abc import Iterator
from dataclasses import asdict, dataclass
from pathlib import Path

from labelhub_evals.canonical import sha256_file, sha256_json
from labelhub_evals.paths import CACHE_DIR, DATASETS_DIR

REPO = "nvidia/HelpSteer2"
REVISION = "990b2711a36180dd19d9c94b8627844866f8982a"
SPLIT_FILE = "validation.jsonl.gz"
SPLIT_SHA256 = "610eeb5289494d613c4c0f70aade2df8df0b499f3a24e76d232f74e6909d010a"
LABEL_MAPPING_VERSION = "helpsteer2-label-mapping-v1"
SMOKE_FILE = DATASETS_DIR / "helpsteer2-smoke.jsonl"
SMOKE_SEED = 20261102
SMOKE_PER_CLASS = 20


def ground_truth(helpfulness: int, correctness: int) -> str:
    """Label mapping v1 (0-4 human scores -> what a careful reviewer should decide).

    accept     helpfulness >= 3 and correctness >= 3
    reject     helpfulness <= 1 or  correctness <= 1
    borderline everything else; routing it to a human is the correct outcome
    """
    if helpfulness <= 1 or correctness <= 1:
        return "reject"
    if helpfulness >= 3 and correctness >= 3:
        return "accept"
    return "borderline"


@dataclass(frozen=True)
class Case:
    case_id: str
    prompt: str
    response: str
    truth: str
    human_scores: dict[str, int]

    def to_json(self) -> dict:
        return asdict(self)

    @staticmethod
    def from_json(row: dict) -> Case:
        return Case(row["case_id"], row["prompt"], row["response"], row["truth"], row["human_scores"])


def _download(destination: Path) -> Path:
    destination.parent.mkdir(parents=True, exist_ok=True)
    if not destination.exists():
        url = f"https://huggingface.co/datasets/{REPO}/resolve/{REVISION}/{SPLIT_FILE}"
        tmp = destination.with_suffix(".part")
        urllib.request.urlretrieve(url, tmp)
        tmp.replace(destination)
    actual = sha256_file(destination)
    if actual != SPLIT_SHA256:
        raise RuntimeError(f"{destination} sha256 {actual} != pinned {SPLIT_SHA256}; refusing to evaluate on it")
    return destination


def _rows(path: Path) -> Iterator[dict]:
    with gzip.open(path, "rt", encoding="utf-8") as handle:
        for line in handle:
            yield json.loads(line)


def load_full() -> list[Case]:
    """All 1038 validation rows. case_id is a content hash, so it is stable across machines and orderings."""
    path = _download(CACHE_DIR / "helpsteer2" / REVISION / SPLIT_FILE)
    cases = []
    for row in _rows(path):
        scores = {k: int(row[k]) for k in ("helpfulness", "correctness", "coherence", "complexity", "verbosity")}
        case_id = "hs2-" + sha256_json({"prompt": row["prompt"], "response": row["response"]})[:16]
        cases.append(Case(case_id, row["prompt"], row["response"], ground_truth(scores["helpfulness"], scores["correctness"]), scores))
    return cases


def build_smoke(cases: list[Case]) -> list[Case]:
    """Stratified 20 accept / 20 borderline / 20 reject sample: small enough to commit and replay in CI."""
    rng = random.Random(SMOKE_SEED)
    picked: list[Case] = []
    for truth in ("accept", "borderline", "reject"):
        pool = sorted((c for c in cases if c.truth == truth), key=lambda c: c.case_id)
        picked.extend(rng.sample(pool, SMOKE_PER_CLASS))
    return sorted(picked, key=lambda c: c.case_id)


def write_smoke(path: Path = SMOKE_FILE) -> Path:
    smoke = build_smoke(load_full())
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        for case in smoke:
            handle.write(json.dumps(case.to_json(), ensure_ascii=False) + "\n")
    return path


def load_smoke(path: Path = SMOKE_FILE) -> list[Case]:
    with open(path, encoding="utf-8") as handle:
        return [Case.from_json(json.loads(line)) for line in handle if line.strip()]


def load(name: str) -> list[Case]:
    if name == "helpsteer2-smoke":
        return load_smoke()
    if name == "helpsteer2-full":
        return load_full()
    raise ValueError(f"unknown dataset {name!r}; expected helpsteer2-smoke or helpsteer2-full")
