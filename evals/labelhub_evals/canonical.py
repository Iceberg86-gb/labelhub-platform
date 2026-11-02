"""Python mirror of services/api/.../shared/canonical/Canonicalizer.java (conventions §7.2, LH-5).

Canonicalizer.java serializes with Jackson: keys sorted recursively, compact separators, non-ASCII
characters written as raw UTF-8. Hashing is SHA-256 over the UTF-8 bytes, lowercase hex.

Floats are rejected rather than formatted: Jackson and Python print doubles differently, and §7.2
fixes floats at 6 decimals. Callers round to 6 decimals and pass strings, or use ints, so the two
implementations cannot drift. tests/test_canonical.py pins the byte format with shared vectors.
"""

from __future__ import annotations

import hashlib
import json
from typing import Any


def _check(value: Any) -> None:
    if isinstance(value, float):
        raise TypeError("canonical JSON forbids floats; format to 6 decimals as a string (conventions §7.2)")
    if isinstance(value, dict):
        for key, item in value.items():
            if not isinstance(key, str):
                raise TypeError("canonical JSON object keys must be strings")
            _check(item)
    elif isinstance(value, (list, tuple)):
        for item in value:
            _check(item)


def canonical_json(value: Any) -> str:
    _check(value)
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def sha256_hex(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def canonical_hash(value: Any) -> str:
    return sha256_hex(canonical_json(value))


def fixed6(value: float | int | None) -> str | None:
    """Format a number the way §7.2 fixes floats: 6 decimals, half-up."""
    if value is None:
        return None
    from decimal import ROUND_HALF_UP, Decimal

    return str(Decimal(str(value)).quantize(Decimal("0.000001"), rounding=ROUND_HALF_UP))
