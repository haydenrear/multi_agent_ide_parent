"""
ACP event types stored by the event bus for tracing and offline analysis.
"""
from __future__ import annotations

import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _uid() -> str:
    return str(uuid.uuid4())


@dataclass
class BaseEvent:
    event_id: str = field(default_factory=_uid)
    timestamp: datetime = field(default_factory=_now)
    artifact_key: str | None = None


@dataclass
class ChatSessionCreatedEvent(BaseEvent):
    chat_options: str = ""


@dataclass
class ChatSessionResetEvent(BaseEvent):
    session_key: str = ""


@dataclass
class CompactionEvent(BaseEvent):
    session_key: str = ""
    message: str = ""


@dataclass
class NodeErrorEvent(BaseEvent):
    session_key: str = ""
    message: str = ""


@dataclass
class NodeStreamDeltaEvent(BaseEvent):
    session_key: str = ""
    content: str = ""
    token_count: int = 0
    is_final: bool = False


@dataclass
class NodeThoughtDeltaEvent(BaseEvent):
    session_key: str = ""
    content: str = ""
    token_count: int = 0
    is_final: bool = False
    artifact_key: str | None = None


@dataclass
class UserMessageChunkEvent(BaseEvent):
    session_key: str = ""
    content: str = ""
    artifact_key: str | None = None


@dataclass
class ToolCallEvent(BaseEvent):
    session_key: str = ""
    tool_call_id: str = ""
    title: str = ""
    kind: str | None = None
    status: str | None = None
    phase: str = ""
    content: list[dict[str, Any]] = field(default_factory=list)
    locations: list[dict[str, Any]] = field(default_factory=list)
    raw_input: str | None = None
    raw_output: str | None = None
    artifact_key: str | None = None


@dataclass
class PlanUpdateEvent(BaseEvent):
    session_key: str = ""
    entries: list[dict[str, Any]] = field(default_factory=list)


@dataclass
class CurrentModeUpdateEvent(BaseEvent):
    session_key: str = ""
    mode_id: str = ""


@dataclass
class AvailableCommandsUpdateEvent(BaseEvent):
    session_key: str = ""
    commands: list[dict[str, Any]] = field(default_factory=list)


@dataclass
class UsageUpdateEvent(BaseEvent):
    session_key: str = ""
    usage: dict[str, Any] = field(default_factory=dict)


@dataclass
class ConfigOptionUpdateEvent(BaseEvent):
    session_key: str = ""
    config_options: list[dict[str, Any]] = field(default_factory=list)


@dataclass
class SessionInfoUpdateEvent(BaseEvent):
    session_key: str = ""
    info: dict[str, Any] = field(default_factory=dict)


@dataclass
class PermissionRequestEvent(BaseEvent):
    session_key: str = ""
    tool_call_id: str = ""
    tool_name: str = ""


@dataclass
class PermissionDecisionEvent(BaseEvent):
    session_key: str = ""
    tool_call_id: str = ""
    tool_name: str = ""
    allowed: bool = False


AcpEvent = (
    ChatSessionCreatedEvent
    | ChatSessionResetEvent
    | CompactionEvent
    | NodeErrorEvent
    | NodeStreamDeltaEvent
    | NodeThoughtDeltaEvent
    | UserMessageChunkEvent
    | ToolCallEvent
    | PlanUpdateEvent
    | CurrentModeUpdateEvent
    | AvailableCommandsUpdateEvent
    | UsageUpdateEvent
    | ConfigOptionUpdateEvent
    | SessionInfoUpdateEvent
    | PermissionRequestEvent
    | PermissionDecisionEvent
)
