"""
ArtifactKey — hierarchical ULID-based identifier for tracing LLM call chains.

Format: ak:<ulid>(/ulid)*
"""
from __future__ import annotations

import os
import re
import time
from typing import Optional

_CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
_PATTERN = re.compile(r"^ak:[0-9A-HJKMNP-TV-Z]{26}(/[0-9A-HJKMNP-TV-Z]{26})*$")


def _ulid() -> str:
    ms = int(time.time() * 1000) & 0xFFFF_FFFF_FFFF

    ts_chars = []
    for _ in range(10):
        ts_chars.append(_CROCKFORD[ms & 0x1F])
        ms >>= 5
    ts_str = "".join(reversed(ts_chars))

    rand = int.from_bytes(os.urandom(10), "big")
    rand_chars = []
    for _ in range(16):
        rand_chars.append(_CROCKFORD[rand & 0x1F])
        rand >>= 5
    rand_str = "".join(reversed(rand_chars))

    return ts_str + rand_str


class ArtifactKey:
    """Hierarchical ULID-based identifier for tracing ACP work."""

    __slots__ = ("value",)

    def __init__(self, value: str) -> None:
        self.value = value

    @classmethod
    def create_root(cls) -> "ArtifactKey":
        return cls(f"ak:{_ulid()}")

    @classmethod
    def from_string(cls, value: str) -> "ArtifactKey":
        key = cls(value)
        if not key.is_valid():
            raise ValueError(
                f"Invalid ArtifactKey format: {value!r}. "
                "Expected 'ak:<26-char-ulid>(/ulid)*'."
            )
        return key

    def create_child(self) -> "ArtifactKey":
        return ArtifactKey(f"{self.value}/{_ulid()}")

    def parent(self) -> Optional["ArtifactKey"]:
        idx = self.value.rfind("/")
        if idx < 0:
            return None
        return ArtifactKey(self.value[:idx])

    def depth(self) -> int:
        return self.value.count("/")

    def is_valid(self) -> bool:
        return bool(_PATTERN.match(self.value))

    def __repr__(self) -> str:
        return f"ArtifactKey({self.value!r})"

    def __str__(self) -> str:
        return self.value

    def __eq__(self, other: object) -> bool:
        if isinstance(other, ArtifactKey):
            return self.value == other.value
        return NotImplemented

    def __hash__(self) -> int:
        return hash(self.value)
