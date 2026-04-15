"""
AcpStreamWindowBuffer — typed streaming content aggregator.
"""
from __future__ import annotations

import logging
from dataclasses import dataclass, field
from enum import Enum, auto
from typing import TYPE_CHECKING, Any

from acp.schema import (
    AgentMessageChunk,
    AgentPlanUpdate,
    AgentThoughtChunk,
    AvailableCommandsUpdate,
    ConfigOptionUpdate,
    ContentToolCallContent,
    CurrentModeUpdate,
    FileEditToolCallContent,
    SessionInfoUpdate,
    TerminalToolCallContent,
    TextContentBlock,
    ToolCallProgress,
    ToolCallStart,
    UsageUpdate,
    UserMessageChunk,
)

from .event_bus import EventPublisher
from .events import (
    AvailableCommandsUpdateEvent,
    BaseEvent,
    ConfigOptionUpdateEvent,
    CurrentModeUpdateEvent,
    NodeStreamDeltaEvent,
    NodeThoughtDeltaEvent,
    PlanUpdateEvent,
    SessionInfoUpdateEvent,
    ToolCallEvent,
    UsageUpdateEvent,
    UserMessageChunkEvent,
)

if TYPE_CHECKING:
    from .artifact_key import ArtifactKey

logger = logging.getLogger(__name__)


class WindowType(Enum):
    MESSAGE = auto()
    THOUGHT = auto()
    TOOL_CALL = auto()
    PLAN = auto()
    USER_MESSAGE = auto()
    CURRENT_MODE = auto()
    AVAILABLE_COMMANDS = auto()


@dataclass
class _Window:
    node_id: str
    wtype: WindowType
    session_key: str
    buffer: str = ""
    token_count: int = 0
    events: list[Any] = field(default_factory=list)

    def append_text(self, text: str) -> None:
        self.buffer += text
        self.token_count += 1

    def append_event(self, event: Any) -> None:
        self.events.append(event)


class AcpStreamWindowBuffer:
    """Buffers streamed ACP updates into windowed events."""

    def __init__(self, event_bus: EventPublisher[BaseEvent]) -> None:
        self._bus = event_bus
        self._windows: dict[tuple[str, WindowType], _Window] = {}
        self.message_parent_key: "ArtifactKey | None" = None

    def append_text(
        self, node_id: str, session_key: str, wtype: WindowType, text: str
    ) -> list[str]:
        flushed = self._flush_other(node_id, session_key, keep=wtype)
        self._get_or_create(node_id, session_key, wtype).append_text(text)
        return flushed

    def append_event(
        self, node_id: str, session_key: str, wtype: WindowType, event: Any
    ) -> list[str]:
        flushed = self._flush_other(node_id, session_key, keep=wtype)
        self._get_or_create(node_id, session_key, wtype).append_event(event)
        return flushed

    def publish_metadata(self, event: BaseEvent) -> None:
        """Publish a metadata event (usage, config, session_info) directly.

        Metadata updates are side-channel data — they should NOT flush content
        windows (MESSAGE, TOOL_CALL, etc.). This matches the Java side where
        UsageUpdate/ConfigOptionUpdate/SessionInfoUpdate return empty lists
        without calling flushOtherWindows.
        """
        self._bus.publish(event)

    def flush_windows(self, node_id: str) -> list[str]:
        keys = [key for key in self._windows if key[0] == node_id]
        return [result for key in keys if (result := self._flush_window(key, is_final=True)) is not None]

    def flush_all(self) -> list[str]:
        return [
            result
            for key in list(self._windows)
            if (result := self._flush_window(key, is_final=True)) is not None
        ]

    def _get_or_create(self, node_id: str, session_key: str, wtype: WindowType) -> _Window:
        key = (node_id, wtype)
        if key not in self._windows:
            self._windows[key] = _Window(node_id=node_id, wtype=wtype, session_key=session_key)
        return self._windows[key]

    def _flush_other(self, node_id: str, session_key: str, keep: WindowType) -> list[str]:
        keys = [key for key in self._windows if key[0] == node_id and key[1] != keep]
        return [result for key in keys if (result := self._flush_window(key, is_final=True)) is not None]

    def _flush_window(self, key: tuple[str, WindowType], is_final: bool) -> str | None:
        window = self._windows.pop(key, None)
        if window is None:
            return None

        artifact_key_str: str | None = None
        if self.message_parent_key is not None:
            artifact_key_str = self.message_parent_key.create_child().value

        match window.wtype:
            case WindowType.MESSAGE:
                if window.buffer:
                    self._bus.publish(
                        NodeStreamDeltaEvent(
                            session_key=window.session_key,
                            content=window.buffer,
                            token_count=window.token_count,
                            is_final=is_final,
                            artifact_key=artifact_key_str,
                        )
                    )
                    return window.buffer

            case WindowType.THOUGHT:
                if window.buffer:
                    self._bus.publish(
                        NodeThoughtDeltaEvent(
                            session_key=window.session_key,
                            content=window.buffer,
                            token_count=window.token_count,
                            is_final=is_final,
                            artifact_key=artifact_key_str,
                        )
                    )

            case WindowType.USER_MESSAGE:
                if window.buffer:
                    self._bus.publish(
                        UserMessageChunkEvent(
                            session_key=window.session_key,
                            content=window.buffer,
                            artifact_key=artifact_key_str,
                        )
                    )

            case (
                WindowType.TOOL_CALL
                | WindowType.PLAN
                | WindowType.CURRENT_MODE
                | WindowType.AVAILABLE_COMMANDS
            ):
                for event in window.events:
                    if artifact_key_str is not None and hasattr(event, "artifact_key"):
                        event.artifact_key = artifact_key_str
                    self._bus.publish(event)

            case _:
                raise ValueError(f"Unhandled WindowType: {window.wtype!r}")

        return None


def route_acp_update(
    update: Any,
    node_id: str,
    session_key: str,
    buf: AcpStreamWindowBuffer,
) -> list[str]:
    match update.session_update:
        case "agent_message_chunk":
            text = _chunk_text(update)
            return buf.append_text(node_id, session_key, WindowType.MESSAGE, text)

        case "agent_thought_chunk":
            return buf.append_text(node_id, session_key, WindowType.THOUGHT, _chunk_text(update))

        case "user_message_chunk":
            return buf.append_text(node_id, session_key, WindowType.USER_MESSAGE, _chunk_text(update))

        case "tool_call":
            event = ToolCallEvent(
                session_key=session_key,
                tool_call_id=update.tool_call_id,
                title=update.title,
                kind=update.kind,
                status=update.status,
                phase="START",
                content=[_tool_content_to_dict(content) for content in (update.content or [])],
                locations=[{"path": location.path, "line": location.line} for location in (update.locations or [])],
                raw_input=str(update.raw_input) if update.raw_input is not None else None,
            )
            return buf.append_event(node_id, session_key, WindowType.TOOL_CALL, event)

        case "tool_call_update":
            phase = _tool_call_phase(update.status)
            event = ToolCallEvent(
                session_key=session_key,
                tool_call_id=update.tool_call_id,
                title=update.title or "tool_call",
                kind=update.kind,
                status=update.status,
                phase=phase,
                content=[_tool_content_to_dict(content) for content in (update.content or [])],
                locations=[{"path": location.path, "line": location.line} for location in (update.locations or [])],
                raw_input=str(update.raw_input) if update.raw_input is not None else None,
                raw_output=str(update.raw_output) if update.raw_output is not None else None,
            )
            return buf.append_event(node_id, session_key, WindowType.TOOL_CALL, event)

        case "plan":
            event = PlanUpdateEvent(
                session_key=session_key,
                entries=[
                    {"content": entry.content, "priority": entry.priority, "status": entry.status}
                    for entry in update.entries
                ],
            )
            return buf.append_event(node_id, session_key, WindowType.PLAN, event)

        case "current_mode_update":
            event = CurrentModeUpdateEvent(session_key=session_key, mode_id=update.current_mode_id)
            return buf.append_event(node_id, session_key, WindowType.CURRENT_MODE, event)

        case "available_commands_update":
            event = AvailableCommandsUpdateEvent(
                session_key=session_key,
                commands=[
                    {"name": command.name, "description": command.description}
                    for command in update.available_commands
                ],
            )
            return buf.append_event(node_id, session_key, WindowType.AVAILABLE_COMMANDS, event)

        case "usage_update":
            buf.publish_metadata(UsageUpdateEvent(
                session_key=session_key,
                usage=update.usage.model_dump() if hasattr(update.usage, "model_dump") else {},
            ))
            return []

        case "config_option_update":
            buf.publish_metadata(ConfigOptionUpdateEvent(
                session_key=session_key,
                config_options=[
                    option.model_dump() if hasattr(option, "model_dump") else {}
                    for option in update.config_options
                ],
            ))
            return []

        case "session_info_update":
            buf.publish_metadata(SessionInfoUpdateEvent(
                session_key=session_key,
                info=update.info if isinstance(update.info, dict) else {},
            ))
            return []

        case _:
            raise ValueError(
                f"Unhandled ACP session_update discriminator: {update.session_update!r}. "
                "Add a new case arm to route_acp_update()."
            )


def _chunk_text(chunk: AgentMessageChunk | AgentThoughtChunk | UserMessageChunk) -> str:
    content = chunk.content
    # content may be a single TextContentBlock or a list of content blocks,
    # depending on the ACP SDK version.
    if isinstance(content, TextContentBlock):
        return content.text or ""
    if isinstance(content, list):
        return "".join(
            block.text for block in content if isinstance(block, TextContentBlock)
        )
    # Fallback: check for a .text attribute on the content object
    text = getattr(content, "text", None)
    if text is not None:
        return str(text)
    return ""


def _tool_call_phase(status: str | None) -> str:
    if status == "completed":
        return "RESULT"
    return "UPDATE"


def _tool_content_to_dict(content: ContentToolCallContent) -> dict[str, Any]:
    if isinstance(content, TextContentBlock):
        return {"type": "text", "text": content.text}
    if isinstance(content, FileEditToolCallContent):
        return {
            "type": "file_edit",
            "path": content.path,
            "old_text": content.old_text,
            "new_text": content.new_text,
        }
    if isinstance(content, TerminalToolCallContent):
        return {
            "type": "terminal",
            "command": content.command,
            "output": content.output,
            "exit_code": content.exit_code,
        }
    if hasattr(content, "model_dump"):
        return content.model_dump()
    return {"type": type(content).__name__}
