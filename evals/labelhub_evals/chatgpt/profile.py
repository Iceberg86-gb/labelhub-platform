"""Model profile with draft -> tested -> applied lifecycle and rollback points (ADR-018 §3).

apply() only succeeds when the stored test proof matches (a) the current draft, (b) the signed-in
account, and (c) the profile version the test was taken against. Every apply pushes the previous
active selection onto the rollback stack. Writes are optimistic: a concurrent writer bumps the
version and the slower writer fails instead of silently overwriting.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

from labelhub_evals.canonical import canonical_hash
from labelhub_evals.chatgpt.credentials import _atomic_write

MAX_ROLLBACK = 10


class ProfileError(RuntimeError):
    pass


@dataclass
class Selection:
    model: str
    effort: str | None

    def to_json(self) -> dict:
        return {"effort": self.effort, "model": self.model}

    @staticmethod
    def from_json(data: dict | None) -> "Selection | None":
        return None if not data else Selection(data["model"], data.get("effort"))

    def identity(self) -> str:
        return f"chatgpt/{self.model}" + (f"@{self.effort}" if self.effort else "")


def _empty() -> dict:
    return {"version": 0, "account_key": None, "active": None, "draft": None, "test": None, "rollback": []}


class ProfileStore:
    def __init__(self, home: Path):
        self.path = home / "chatgpt-profile.json"

    def load(self) -> dict:
        if not self.path.exists():
            return _empty()
        return json.loads(self.path.read_text(encoding="utf-8"))

    def _save(self, profile: dict, expected_version: int) -> dict:
        current = self.load()
        if current["version"] != expected_version:
            raise ProfileError("profile changed since it was read (another process?); re-run the command")
        profile = dict(profile, version=expected_version + 1)
        _atomic_write(self.path, (json.dumps(profile, indent=2, sort_keys=True) + "\n").encode("utf-8"))
        return profile

    # --- lifecycle --------------------------------------------------------------------------------
    def select(self, account_key: str, selection: Selection, source: str) -> dict:
        profile = self.load()
        self._bind_account(profile, account_key)
        draft = dict(selection.to_json(), source=source)
        return self._save(dict(profile, draft=draft, test=None), profile["version"])

    def record_test(self, account_key: str, passed: bool, detail: str) -> dict:
        profile = self.load()
        self._bind_account(profile, account_key)
        if profile["draft"] is None:
            raise ProfileError("no draft to test; run `chatgpt select` first")
        proof = {
            "account_key": account_key,
            "base_version": profile["version"] + 1,  # the version this save will produce
            "detail": detail,
            "draft_hash": _draft_hash(profile["draft"]),
            "passed": passed,
        }
        return self._save(dict(profile, test=proof), profile["version"])

    def apply(self, account_key: str) -> dict:
        profile = self.load()
        draft, proof = profile["draft"], profile["test"]
        if draft is None:
            raise ProfileError("no draft to apply")
        if proof is None or not proof["passed"]:
            raise ProfileError("draft has not passed `chatgpt test`; current configuration kept")
        if proof["account_key"] != account_key or profile["account_key"] != account_key:
            raise ProfileError("signed-in account differs from the one the test ran under; re-run `chatgpt test`")
        if proof["draft_hash"] != _draft_hash(draft):
            raise ProfileError("draft changed after it was tested; re-run `chatgpt test`")
        if proof["base_version"] != profile["version"]:
            raise ProfileError("profile changed after the test; re-run `chatgpt test`")
        rollback = list(profile["rollback"])
        if profile["active"] is not None:
            rollback.append(dict(profile["active"], replaced_at_version=profile["version"]))
        active = {"effort": draft["effort"], "model": draft["model"]}
        return self._save(dict(profile, active=active, draft=None, test=None, rollback=rollback[-MAX_ROLLBACK:]), profile["version"])

    def rollback(self, account_key: str) -> dict:
        profile = self.load()
        if profile["account_key"] not in (None, account_key):
            raise ProfileError("profile belongs to a different account")
        if not profile["rollback"]:
            raise ProfileError("no rollback point")
        rollback = list(profile["rollback"])
        previous = rollback.pop()
        active = {"effort": previous["effort"], "model": previous["model"]}
        return self._save(dict(profile, active=active, draft=None, test=None, rollback=rollback), profile["version"])

    def active(self) -> Selection | None:
        return Selection.from_json(self.load()["active"])

    @staticmethod
    def _bind_account(profile: dict, account_key: str) -> None:
        if profile["account_key"] is None:
            profile["account_key"] = account_key
        elif profile["account_key"] != account_key:
            # A different account must not inherit a model choice tested elsewhere.
            profile.update(account_key=account_key, active=None, draft=None, test=None, rollback=[])


def _draft_hash(draft: dict) -> str:
    return canonical_hash({"effort": draft.get("effort"), "model": draft["model"]})
