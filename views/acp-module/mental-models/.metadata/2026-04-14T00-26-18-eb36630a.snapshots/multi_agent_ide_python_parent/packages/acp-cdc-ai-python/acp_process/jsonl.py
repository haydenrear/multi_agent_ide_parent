"""
Reusable JSONL persistence helpers.
"""
from __future__ import annotations

import json
import re
from collections.abc import Callable, Mapping
from dataclasses import asdict, is_dataclass
from pathlib import Path
from typing import Any, Generic, TypeVar

from .events import BaseEvent

EventT = TypeVar("EventT")
JsonlSerializer = Callable[[EventT], Mapping[str, Any]]
RoutingKeyResolver = Callable[[EventT], str | None]
_SAFE_PATH_CHARS_RE = re.compile(r"[^A-Za-z0-9._-]+")


def default_jsonl_record(event: Any) -> dict[str, Any]:
    if hasattr(event, "model_dump"):
        payload = event.model_dump()
    elif is_dataclass(event):
        payload = asdict(event)
    elif isinstance(event, Mapping):
        payload = dict(event)
    elif hasattr(event, "__dict__"):
        payload = dict(event.__dict__)
    else:
        raise TypeError(f"Unsupported JSONL payload type: {type(event)!r}")

    return {"_type": type(event).__name__, **payload}


def _sanitize_routing_key(value: str, default: str) -> str:
    safe = _SAFE_PATH_CHARS_RE.sub("_", value).strip("._")
    return safe or default


class JsonlPersistenceListener(Generic[EventT]):
    """Append serialized records to a single JSONL file."""

    def __init__(
        self,
        path: Path,
        serializer: JsonlSerializer[EventT] | None = None,
    ) -> None:
        self._path = path
        self._path.parent.mkdir(parents=True, exist_ok=True)
        self._serializer = serializer or default_jsonl_record

    @property
    def path(self) -> Path:
        return self._path

    def write(self, event: EventT) -> Path:
        record = dict(self._serializer(event))
        line = json.dumps(record, default=str)
        with self._path.open("a", encoding="utf-8") as fh:
            fh.write(line + "\n")
        return self._path

    async def __call__(self, event: EventT) -> None:
        self.write(event)


class RoutedJsonlPersistenceListener(Generic[EventT]):
    """Append serialized records to one JSONL file per routing key."""

    def __init__(
        self,
        log_dir: Path,
        routing_key: RoutingKeyResolver[EventT],
        serializer: JsonlSerializer[EventT] | None = None,
        default_routing_key: str = "_unrouted",
    ) -> None:
        self._dir = log_dir
        self._dir.mkdir(parents=True, exist_ok=True)
        self._routing_key = routing_key
        self._serializer = serializer or default_jsonl_record
        self._default_routing_key = default_routing_key

    @property
    def log_dir(self) -> Path:
        return self._dir

    def path_for(self, routing_key: str) -> Path:
        safe = _sanitize_routing_key(routing_key, self._default_routing_key)
        return self._dir / f"{safe}.jsonl"

    def resolve_routing_key(self, event: EventT) -> str:
        return self._routing_key(event) or self._default_routing_key

    def write(self, event: EventT) -> Path:
        routing_key = self.resolve_routing_key(event)
        path = self.path_for(routing_key)
        record = dict(self._serializer(event))
        line = json.dumps(record, default=str)
        with path.open("a", encoding="utf-8") as fh:
            fh.write(line + "\n")
        return path

    async def __call__(self, event: EventT) -> None:
        self.write(event)


def acp_event_routing_key(event: BaseEvent) -> str | None:
    return getattr(event, "artifact_key", None) or getattr(event, "session_key", None)


class SessionJsonlPersistenceListener(RoutedJsonlPersistenceListener[BaseEvent]):
    """ACP-specific per-session JSONL routing."""

    def __init__(self, log_dir: Path, default_routing_key: str = "_unrouted") -> None:
        super().__init__(
            log_dir=log_dir,
            routing_key=acp_event_routing_key,
            serializer=default_jsonl_record,
            default_routing_key=default_routing_key,
        )
