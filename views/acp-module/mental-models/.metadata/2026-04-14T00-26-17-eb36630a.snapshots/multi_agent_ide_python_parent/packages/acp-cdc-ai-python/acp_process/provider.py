"""
ACPLLMProvider — drop-in replacement for an LLM provider backed by an ACP agent.
"""
from __future__ import annotations

import contextvars
import hashlib
import json
import logging
import os
from pathlib import Path
from typing import Any

from acp import text_block
from acp.schema import EnvVariable, HttpMcpServer, McpServerStdio, SseMcpServer

from .artifact_key import ArtifactKey
from .event_bus import AsyncEventBus, EventBus, EventListener, EventPublisher
from .events import BaseEvent, NodeErrorEvent
from .jsonl import SessionJsonlPersistenceListener
from .session import AcpSessionManager, SessionConfig

logger = logging.getLogger(__name__)


class AcpRuntime:
    """
    Owns ACP shared infrastructure so applications can embed the runtime
    without relying on module-level globals.
    """

    def __init__(self, event_bus: EventBus[BaseEvent] | None = None) -> None:
        self._event_bus = event_bus or AsyncEventBus[BaseEvent]()
        self._session_manager = AcpSessionManager(self._event_bus)
        self._event_log_listener: SessionJsonlPersistenceListener | None = None

    @property
    def event_bus(self) -> EventBus[BaseEvent]:
        return self._event_bus

    @property
    def session_manager(self) -> AcpSessionManager:
        return self._session_manager

    def add_listener(self, listener: EventListener[BaseEvent]) -> None:
        self._event_bus.add_listener(listener)

    def remove_listener(self, listener: EventListener[BaseEvent]) -> None:
        self._event_bus.remove_listener(listener)

    def configure_event_log(self, event_log_dir: Path | None) -> None:
        if self._event_log_listener is not None:
            self._event_bus.remove_listener(self._event_log_listener)
            self._event_log_listener = None

        if event_log_dir is None:
            return

        self._event_log_listener = SessionJsonlPersistenceListener(event_log_dir)
        self._event_bus.add_listener(self._event_log_listener)
        logger.info("ACP event persistence enabled -> %s", event_log_dir)

    async def start(self) -> None:
        await self._event_bus.start()

    async def shutdown(self) -> None:
        await self._session_manager.shutdown_all()
        await self._event_bus.stop()


_default_runtime = AcpRuntime()
_current_artifact_key: contextvars.ContextVar[ArtifactKey | None] = contextvars.ContextVar(
    "acp_artifact_key",
    default=None,
)


def get_default_runtime() -> AcpRuntime:
    return _default_runtime


def get_event_bus() -> EventBus[BaseEvent]:
    return get_default_runtime().event_bus


def get_session_manager() -> AcpSessionManager:
    return get_default_runtime().session_manager


async def init_acp(event_log_dir: Path | None = None, runtime: AcpRuntime | None = None) -> None:
    active_runtime = runtime or get_default_runtime()
    active_runtime.configure_event_log(event_log_dir)
    await active_runtime.start()
    logger.info(
        "ACP event bus started (command=%s args=%s pool_size=%s model=%s)",
        os.getenv("HINDSIGHT_API_LLM_ACP_COMMAND", "claude"),
        os.getenv("HINDSIGHT_API_LLM_ACP_ARGS", "--acp"),
        os.getenv("HINDSIGHT_API_LLM_ACP_POOL_SIZE", "3"),
        os.getenv("HINDSIGHT_API_LLM_ACP_MODEL", "<default>"),
    )


async def shutdown_acp(runtime: AcpRuntime | None = None) -> None:
    await (runtime or get_default_runtime()).shutdown()


def parse_mcp_server(d: dict) -> HttpMcpServer | SseMcpServer | McpServerStdio:
    server_type = d.get("type", "http")
    match server_type:
        case "http":
            from acp.schema import HttpHeader

            return HttpMcpServer(
                name=d["name"],
                url=d["url"],
                headers=[HttpHeader(**header) for header in d.get("headers", [])],
            )
        case "sse":
            return SseMcpServer(name=d["name"], url=d["url"])
        case "stdio":
            return McpServerStdio(
                name=d["name"],
                command=d["command"],
                args=d.get("args", []),
                env=[EnvVariable(name=key, value=value) for key, value in d.get("env", {}).items()]
                if isinstance(d.get("env"), dict)
                else [EnvVariable(**env_var) for env_var in d.get("env", [])],
            )
        case _:
            raise ValueError(
                f"Unknown MCP server type: {server_type!r}. "
                "Expected 'http', 'sse', or 'stdio'."
            )


class ACPLLMProvider:
    """Wrap an ACP agent as an LLM provider."""

    def __init__(
        self,
        command: str,
        args: list[str],
        mcp_servers: list[dict] | None = None,
        cwd: str = ".",
        model: str | None = None,
        env: dict[str, str] | None = None,
        max_pool_size: int = 3,
        runtime: AcpRuntime | None = None,
    ) -> None:
        self._runtime = runtime or get_default_runtime()
        self._cfg = SessionConfig(
            command=command,
            args=args,
            mcp_servers=[parse_mcp_server(server) for server in (mcp_servers or [])],
            cwd=cwd,
            model=model,
            env=env,
            max_pool_size=max_pool_size,
        )
        self._routing_key = _fingerprint(command, args, mcp_servers or [])

    async def call(
        self,
        messages: list[dict[str, str]],
        response_format: Any | None = None,
        max_completion_tokens: int | None = None,
        temperature: float | None = None,
        scope: str = "memory",
        max_retries: int = 3,
        max_parse_retries: int = 10,
        skip_validation: bool = False,
        **_kwargs: Any,
    ) -> Any | None:
        pool = self._runtime.session_manager.get_pool(self._routing_key, self._cfg)
        prompt = self._to_acp_prompt(messages, response_format)

        request_key = _current_artifact_key.get()
        if request_key is None:
            request_key = ArtifactKey.create_root()
            _current_artifact_key.set(request_key)
            logger.warning(
                "No request-level artifact key in context (scope=%s) — created root %s",
                scope,
                request_key.value,
            )

        call_key = request_key.create_child()

        async with pool.acquire(artifact_key=call_key) as session:
            session._buf.message_parent_key = call_key
            results = await session.prompt(prompt)
            raw = "\n".join(results).strip()

            if response_format is None:
                return raw

            return await _parse_with_retries(
                raw=raw,
                session=session,
                response_format=response_format,
                max_parse_retries=max_parse_retries,
                scope=scope,
                skip_validation=skip_validation,
                event_bus=self._runtime.event_bus,
            )

    async def verify_connection(self) -> None:
        result = await self.call(messages=[{"role": "user", "content": "Say 'ok'"}])
        logger.info("ACP agent verified: %r", str(result)[:60])

    @classmethod
    def from_env(cls, runtime: AcpRuntime | None = None) -> "ACPLLMProvider":
        return cls(
            command=os.getenv("HINDSIGHT_API_LLM_ACP_COMMAND", "claude"),
            args=os.getenv("HINDSIGHT_API_LLM_ACP_ARGS", "--acp").split(),
            mcp_servers=json.loads(os.getenv("HINDSIGHT_API_LLM_ACP_MCP_SERVERS", "[]")),
            cwd=os.getenv("HINDSIGHT_API_LLM_ACP_CWD", "."),
            model=os.getenv("HINDSIGHT_API_LLM_ACP_MODEL") or None,
            max_pool_size=int(os.getenv("HINDSIGHT_API_LLM_ACP_POOL_SIZE", "3")),
            runtime=runtime,
        )

    @classmethod
    def for_memory(cls, **kwargs: Any) -> "ACPLLMProvider":
        return cls(**kwargs)

    @classmethod
    def for_answer_generation(cls, **kwargs: Any) -> "ACPLLMProvider":
        return cls(**kwargs)

    @classmethod
    def for_judge(cls, **kwargs: Any) -> "ACPLLMProvider":
        return cls(**kwargs)

    def _to_acp_prompt(
        self,
        messages: list[dict[str, str]],
        response_format: Any | None,
    ) -> list:
        system_parts: list[str] = []
        other_parts: list[str] = []

        for message in messages:
            role = message["role"]
            content = message["content"]
            match role:
                case "system":
                    system_parts.append(f"<system>\n{content}\n</system>")
                case "assistant":
                    other_parts.append(f"<assistant>\n{content}\n</assistant>")
                case "user":
                    other_parts.append(content)
                case _:
                    raise ValueError(f"Unhandled message role: {role!r}")

        parts = system_parts + other_parts

        if response_format is not None and hasattr(response_format, "model_json_schema"):
            schema = response_format.model_json_schema()
            parts.append(
                "\n\nRespond with a raw JSON object matching this schema. "
                "Output ONLY the JSON — no markdown fences, no Python syntax, "
                "no code execution, no explanation:\n"
                + json.dumps(schema, indent=2)
            )

        return [text_block("\n\n".join(parts))]


def _parse_structured(raw: str, response_format: Any, skip_validation: bool = False) -> Any:
    text = raw
    if "```json" in text:
        text = text.split("```json", 1)[1].split("```", 1)[0]
    elif "```" in text:
        text = text.split("```", 1)[1].split("```", 1)[0]
    data = json.loads(text.strip())
    if hasattr(response_format, "model_validate") and not skip_validation:
        return response_format.model_validate(data)
    return data


async def _parse_with_retries(
    raw: str,
    session: Any,
    response_format: Any,
    max_parse_retries: int,
    scope: str,
    skip_validation: bool = False,
    event_bus: EventPublisher[BaseEvent] | None = None,
) -> Any | None:
    schema: dict | None = None
    if hasattr(response_format, "model_json_schema"):
        schema = response_format.model_json_schema()

    current_raw = raw

    for attempt in range(1, max_parse_retries + 1):
        try:
            return _parse_structured(current_raw, response_format, skip_validation=skip_validation)
        except Exception as exc:
            if attempt == max_parse_retries:
                logger.error(
                    "Structured parse failed after %d attempts (scope=%s). "
                    "Final error: %s. Last raw response: %.400r",
                    max_parse_retries,
                    scope,
                    exc,
                    current_raw,
                )
                if event_bus is not None:
                    artifact_key = getattr(getattr(session, "_artifact_key", None), "value", "unknown")
                    event_bus.publish(
                        NodeErrorEvent(
                            session_key=artifact_key,
                            artifact_key=artifact_key,
                            message=(
                                f"[{scope}] Structured parse exhausted {max_parse_retries} retries. "
                                f"Final error: {exc}"
                            ),
                        )
                    )
                return None

            logger.warning(
                "Structured parse attempt %d/%d failed (scope=%s): %s",
                attempt,
                max_parse_retries,
                scope,
                exc,
            )
            correction = _build_correction_prompt(
                error=exc,
                raw=current_raw,
                schema=schema,
                attempt=attempt,
                max_attempts=max_parse_retries,
            )
            results = await session.prompt([text_block(correction)])
            current_raw = "\n".join(results).strip()

    return None


def _build_correction_prompt(
    error: Exception,
    raw: str,
    schema: dict | None,
    attempt: int,
    max_attempts: int,
) -> str:
    schema_block = (
        f"\nRequired JSON schema:\n{json.dumps(schema, indent=2)}" if schema is not None else ""
    )
    return (
        f"Your previous response could not be parsed as valid JSON "
        f"(attempt {attempt}/{max_attempts}).\n\n"
        f"Parse error:\n{error}\n\n"
        f"Your response was:\n{raw}\n"
        f"{schema_block}\n\n"
        "Please respond again with ONLY a valid JSON object matching the schema above. "
        "Do not include any explanation, markdown code fences, or surrounding text — "
        "just the raw JSON object."
    )


def _fingerprint(*parts: Any) -> str:
    payload = json.dumps(parts, sort_keys=True, default=str)
    return hashlib.sha256(payload.encode()).hexdigest()[:16]
