"""H4 precedent pool for offline evals (ADR-021 §5).

The pool is the HelpSteer2 **train** split at the pinned revision: human-labelled (prompt, response)
pairs with the same truth rule as the golden set. It shares no prompt with validation, so a precedent
can never be the item under review or its sibling answer; retrieval still excludes same-prompt docs.

`precedents build` writes, under datasets/helpsteer2-h4/:
- pool.jsonl         every pool doc (gitignored, rebuilt from the pinned source),
- retrieval.jsonl    top-MAX_K pool keys per golden item (committed: ids only),
- smoke-pool.jsonl   just the pool docs the smoke items reference (committed, so `make evals` is offline),
- manifest.json      hashes of all of the above.
Runs read retrieval.jsonl and resolve keys from pool.jsonl, or smoke-pool.jsonl when that is all there is.
"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path

from labelhub_evals import golden, precedents
from labelhub_evals.canonical import canonical_hash, canonical_json

POOL_FILE = "train.jsonl.gz"
POOL_SHA256 = "c0d7e91d738d42e8a08070db26c4c09a9c7631308e1f0fd380ff43d130c9f713"
MAX_K = 5


class PoolError(RuntimeError):
    pass


@dataclass(frozen=True)
class PoolDoc:
    key: str  # canonical hash of {prompt, response}, like golden item ids
    source_index: int
    content: dict
    verdict: str  # approved | rejected (truth rule hs2-pass-v1 applied to the human ratings)

    def to_json(self) -> dict:
        return {"content": self.content, "key": self.key, "source_index": self.source_index, "verdict": self.verdict}

    @staticmethod
    def from_json(data: dict) -> PoolDoc:
        return PoolDoc(data["key"], data["source_index"], data["content"], data["verdict"])

    def precedent(self) -> precedents.Precedent:
        # HelpSteer2 ratings are single-level human annotations: source "reviewer".
        return precedents.Precedent(self.key, self.verdict, "reviewer", self.content)


def pool_docs(rows: list[dict]) -> list[PoolDoc]:
    docs, seen = [], set()
    for index, row in enumerate(rows):
        content = golden.review_input(row)
        key = canonical_hash(content)
        if key in seen:
            continue
        seen.add(key)
        docs.append(PoolDoc(key, index, content, "approved" if golden.truth_label(row) == "pass" else "rejected"))
    return docs


def _jsonl(rows) -> bytes:
    return "".join(canonical_json(r) + "\n" for r in rows).encode("utf-8")


def retrieve(docs: list[PoolDoc], items: list[golden.GoldenItem], k: int = MAX_K) -> list[dict]:
    index = precedents.Bm25Index([(d.key, precedents.text_of(d.content)) for d in docs])
    by_prompt: dict[str, set[str]] = {}
    for d in docs:
        by_prompt.setdefault(d.content["prompt"], set()).add(d.key)
    out = []
    for item in items:
        exclude = frozenset(by_prompt.get(item.input["prompt"], set()))
        out.append({"item_id": item.item_id, "precedents": index.search(precedents.text_of(item.input), k, exclude)})
    return out


def build(out_dir: Path, cache_dir: Path, golden_dir: Path, *, check: bool = True, verify_retrieval: bool = True) -> dict:
    rows = golden.read_source(golden.fetch_file(cache_dir, POOL_FILE, POOL_SHA256))
    docs = pool_docs(rows)
    pool_bytes = _jsonl(d.to_json() for d in docs)
    items = golden.load(golden_dir / "golden.jsonl")
    smoke_ids = [i.item_id for i in golden.load(golden_dir / "smoke.jsonl")]
    manifest_path = out_dir / "manifest.json"
    retrieval_path = out_dir / "retrieval.jsonl"
    if verify_retrieval or not retrieval_path.exists():
        retrieval = retrieve(docs, items)
        retrieval_bytes = _jsonl(retrieval)
    else:
        retrieval_bytes = retrieval_path.read_bytes()
        retrieval = [json.loads(line) for line in retrieval_bytes.decode("utf-8").split("\n") if line.strip()]
    by_key = {d.key: d for d in docs}
    smoke_keys = sorted({k for r in retrieval if r["item_id"] in set(smoke_ids) for k in r["precedents"]})
    smoke_bytes = _jsonl(by_key[k].to_json() for k in smoke_keys)
    counts = {"approved": sum(d.verdict == "approved" for d in docs), "rejected": sum(d.verdict == "rejected" for d in docs)}
    manifest = {
        "name": "helpsteer2-h4-precedents",
        "license": "CC-BY-4.0",
        "precedent_version": precedents.PRECEDENT_VERSION,
        "source": {"dataset": golden.SOURCE_DATASET, "revision": golden.SOURCE_REVISION, "file": POOL_FILE, "sha256": POOL_SHA256},
        "label_rule": {"version": golden.LABEL_RULE_VERSION, "rule": golden.LABEL_RULE},
        "pool": {"size": len(docs), "verdict_counts": counts, "sha256": hashlib.sha256(pool_bytes).hexdigest()},
        "retrieval": {"items": len(retrieval), "k": MAX_K, "sha256": hashlib.sha256(retrieval_bytes).hexdigest(),
                      "golden_sha256": hashlib.sha256(golden.jsonl_bytes(items)).hexdigest(),
                      "exclusion": "pool docs with the same prompt as the item"},
        "smoke_pool": {"size": len(smoke_keys), "sha256": hashlib.sha256(smoke_bytes).hexdigest()},
    }
    if check and manifest_path.exists():
        pinned = json.loads(manifest_path.read_text(encoding="utf-8"))
        if pinned != manifest:
            raise PoolError("rebuilt precedent pool/retrieval does not match the committed manifest.json; refusing to overwrite")
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "pool.jsonl").write_bytes(pool_bytes)
    retrieval_path.write_bytes(retrieval_bytes)
    (out_dir / "smoke-pool.jsonl").write_bytes(smoke_bytes)
    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False, sort_keys=True) + "\n", encoding="utf-8")
    return manifest


@dataclass(frozen=True)
class PrecedentSet:
    """Per-item precedents for a run. `retrieval_sha256` goes into the run identity."""

    retrieval_sha256: str
    k: int
    by_item: dict[str, list[precedents.Precedent]]


def load(out_dir: Path, items: list[golden.GoldenItem], k: int) -> PrecedentSet:
    if not 1 <= k <= MAX_K:
        raise PoolError(f"--precedents must be between 1 and {MAX_K}")
    manifest = json.loads((out_dir / "manifest.json").read_text(encoding="utf-8"))
    retrieval_bytes = (out_dir / "retrieval.jsonl").read_bytes()
    if hashlib.sha256(retrieval_bytes).hexdigest() != manifest["retrieval"]["sha256"]:
        raise PoolError("retrieval.jsonl does not match manifest.json")
    retrieval = {r["item_id"]: r["precedents"] for r in (json.loads(line) for line in retrieval_bytes.decode("utf-8").split("\n") if line.strip())}
    docs: dict[str, PoolDoc] = {}
    for name, pinned in (("pool.jsonl", manifest["pool"]["sha256"]), ("smoke-pool.jsonl", manifest["smoke_pool"]["sha256"])):
        path = out_dir / name
        if path.exists():
            data = path.read_bytes()
            if hashlib.sha256(data).hexdigest() != pinned:
                raise PoolError(f"{name} does not match manifest.json; rebuild with `precedents build`")
            docs = {d.key: d for d in (PoolDoc.from_json(json.loads(line)) for line in data.decode("utf-8").split("\n") if line.strip())}
            break
    by_item = {}
    for item in items:
        if item.item_id not in retrieval:
            raise PoolError(f"no retrieval for item {item.item_id}; the golden set changed, rebuild with `precedents build`")
        keys = retrieval[item.item_id][:k]
        missing = [key for key in keys if key not in docs]
        if missing:
            raise PoolError(f"precedent {missing[0]} not in the local pool; run `python -m labelhub_evals precedents build`")
        by_item[item.item_id] = [docs[key].precedent() for key in keys]
    return PrecedentSet(manifest["retrieval"]["sha256"], k, by_item)
