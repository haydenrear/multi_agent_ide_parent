"""
AcpSessionManager / AcpSessionPool / AcpSessionContext
"""
from __future__ import annotations

import asyncio
import logging
import re
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

from acp import (
    PROTOCOL_VERSION,
    Client,
    RequestPermissionResponse,
    spawn_agent_process,
)
from acp.schema import (
    ClientCapabilities,
    EnvVariable,
    FileSystemCapabilities,
    HttpMcpServer,
    McpServerStdio,
    ReadTextFileResponse,
    ReleaseTerminalResponse,
    SseMcpServer,
    WriteTextFileResponse,
)

from .artifact_key import ArtifactKey
from .event_bus import EventPublisher
from .events import (
    BaseEvent,
    ChatSessionCreatedEvent,
    ChatSessionResetEvent,
    CompactionEvent,
    NodeErrorEvent,
)
from .stream_buffer import AcpStreamWindowBuffer, route_acp_update

logger = logging.getLogger(__name__)
_COMPACTION_RE = re.compile(r"Compacting\.\.\.|^\.\.\.$", re.MULTILINE)

McpServer = HttpMcpServer | SseMcpServer | McpServerStdio


@dataclass
class SessionConfig:
    command: str
    args: list[str]
    mcp_servers: list[McpServer] = field(default_factory=list)
    cwd: str = "."
    model: str | None = None
    env: dict[str, str] | None = None
    max_pool_size: int = 3


class _AcpClientAdapter(Client):
    def __init__(
        self,
        buf: AcpStreamWindowBuffer,
        session_key: str,
        cwd: str,
    ) -> None:
        self._buf = buf
        self._session_key = session_key
        self._cwd = cwd
        self._terminals: dict[str, asyncio.subprocess.Process] = {}

    async def session_update(self, session_id: str, update: Any, **kwargs: Any) -> None:
        try:
            route_acp_update(update, node_id=session_id, session_key=self._session_key, buf=self._buf)
        except ValueError:
            logger.warning(
                "Unknown session_update type %r for session %s — ignored",
                getattr(update, "session_update", "?"),
                session_id,
            )

    async def request_permission(
        self, options: list, session_id: str, tool_call: Any, **kwargs: Any
    ) -> RequestPermissionResponse:
        self._buf.flush_all()
        allow_option = next(
            (option for option in options if getattr(option, "kind", "") in ("allow_once", "allow_always")),
            options[0] if options else None,
        )
        if allow_option is not None:
            from acp.schema import AllowedOutcome

            return RequestPermissionResponse(
                outcome=AllowedOutcome(outcome="selected", option_id=allow_option.option_id)
            )

        from acp.schema import DeniedOutcome

        return RequestPermissionResponse(outcome=DeniedOutcome(outcome="cancelled"))

    async def read_text_file(
        self,
        path: str,
        session_id: str,
        limit: int | None = None,
        line: int | None = None,
        **kwargs: Any,
    ) -> ReadTextFileResponse:
        file_path = Path(path)
        if not file_path.exists():
            return ReadTextFileResponse(content="")
        if line is None and limit is None:
            return ReadTextFileResponse(content=file_path.read_text(encoding="utf-8"))
        lines = file_path.read_text(encoding="utf-8").splitlines()
        start = max((line or 1) - 1, 0)
        end = (start + limit) if limit else len(lines)
        return ReadTextFileResponse(content="\n".join(lines[start:end]))

    async def write_text_file(
        self, content: str, path: str, session_id: str, **kwargs: Any
    ) -> WriteTextFileResponse:
        file_path = Path(path)
        file_path.parent.mkdir(parents=True, exist_ok=True)
        file_path.write_text(content, encoding="utf-8")
        return WriteTextFileResponse()

    async def create_terminal(
        self,
        command: str,
        session_id: str,
        args: list[str] | None = None,
        cwd: str | None = None,
        env: list[EnvVariable] | None = None,
        output_byte_limit: int | None = None,
        **kwargs: Any,
    ) -> Any:
        from acp import CreateTerminalResponse
        import uuid

        cmd = [command] + (args or [])
        env_dict = {variable.name: variable.value for variable in (env or [])}
        proc = await asyncio.create_subprocess_exec(
            *cmd,
            cwd=cwd or self._cwd,
            env=env_dict or None,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        terminal_id = str(uuid.uuid4())
        self._terminals[terminal_id] = proc
        return CreateTerminalResponse(terminal_id=terminal_id)

    async def terminal_output(self, session_id: str, terminal_id: str, **kwargs: Any) -> Any:
        from acp import TerminalOutputResponse

        proc = self._terminals.get(terminal_id)
        if proc is None:
            return TerminalOutputResponse(output="", truncated=False)
        stdout, stderr = await proc.communicate()
        combined = stdout.decode(errors="replace")
        if stderr:
            combined += "\nSTDERR:\n" + stderr.decode(errors="replace")
        return TerminalOutputResponse(output=combined, truncated=False)

    async def wait_for_terminal_exit(self, session_id: str, terminal_id: str, **kwargs: Any) -> Any:
        from acp import WaitForTerminalExitResponse

        proc = self._terminals.get(terminal_id)
        if proc is None:
            return WaitForTerminalExitResponse(exit_code=0)
        exit_code = await proc.wait()
        return WaitForTerminalExitResponse(exit_code=exit_code)

    async def kill_terminal(self, session_id: str, terminal_id: str, **kwargs: Any) -> Any:
        from acp import KillTerminalResponse

        proc = self._terminals.pop(terminal_id, None)
        if proc:
            proc.kill()
        return KillTerminalResponse()

    async def release_terminal(
        self, session_id: str, terminal_id: str, **kwargs: Any
    ) -> ReleaseTerminalResponse:
        self._terminals.pop(terminal_id, None)
        return ReleaseTerminalResponse()


class AcpSessionContext:
    """One ACP subprocess handling exactly one queued prompt at a time."""

    def __init__(
        self,
        artifact_key: ArtifactKey,
        config: SessionConfig,
        event_bus: EventPublisher[BaseEvent],
    ) -> None:
        self._artifact_key = artifact_key
        self._cfg = config
        self._bus = event_bus
        self._buf = AcpStreamWindowBuffer(event_bus)
        self._queue: asyncio.Queue[tuple[list | None, asyncio.Future]] = asyncio.Queue()
        self._task: asyncio.Task[None] | None = None

    async def start(self) -> None:
        self._task = asyncio.create_task(
            self._run_session(),
            name=f"acp-session-{self._artifact_key.value}",
        )
        self._bus.publish(ChatSessionCreatedEvent(artifact_key=self._artifact_key.value))

    async def stop(self) -> None:
        task = self._task
        if task is None:
            return

        self._task = None
        loop = asyncio.get_running_loop()
        poison_future: asyncio.Future[None] = loop.create_future()
        await self._queue.put((None, poison_future))
        try:
            await task
        except asyncio.CancelledError:
            pass
        except Exception:
            logger.debug("ACP session %s stopped after an error", self._artifact_key.value, exc_info=True)

    async def prompt(self, content: list) -> list[str]:
        if self._task is not None and self._task.done():
            exc = self._task.exception()
            raise exc if exc is not None else RuntimeError(
                f"ACP session task for {self._artifact_key.value!r} ended unexpectedly"
            )

        loop = asyncio.get_running_loop()
        future: asyncio.Future[list[str]] = loop.create_future()
        await self._queue.put((content, future))
        return await future

    async def _run_session(self) -> None:
        client = _AcpClientAdapter(
            buf=self._buf,
            session_key=self._artifact_key.value,
            cwd=self._cfg.cwd,
        )
        capabilities = ClientCapabilities(
            fs=FileSystemCapabilities(read_text_file=True, write_text_file=True),
            terminal=True,
        )
        env = {key: value for key, value in (self._cfg.env or {}).items()}
        crash_exc: Exception | None = None

        try:
            async with spawn_agent_process(
                client,
                self._cfg.command,
                *self._cfg.args,
                env=env or None,
                cwd=self._cfg.cwd,
            ) as (conn, _proc):
                await conn.initialize(
                    protocol_version=PROTOCOL_VERSION,
                    client_capabilities=capabilities,
                )
                session = await conn.new_session(
                    cwd=self._cfg.cwd,
                    mcp_servers=self._cfg.mcp_servers or None,
                )
                session_id = session.session_id

                if self._cfg.model:
                    try:
                        await conn.set_session_model(model_id=self._cfg.model, session_id=session_id)
                    except Exception:
                        logger.debug("set_session_model not supported by agent %s", self._artifact_key.value)

                while True:
                    content, future = await self._queue.get()
                    if content is None:
                        if not future.done():
                            future.set_result(None)
                        break

                    try:
                        results, session_id = await self._do_prompt(conn, session_id, content)
                        if not future.done():
                            future.set_result(results)
                    except Exception as exc:
                        logger.exception("ACP prompt failed for session %s", self._artifact_key.value)
                        self._bus.publish(
                            NodeErrorEvent(
                                session_key=self._artifact_key.value,
                                artifact_key=self._artifact_key.value,
                                message=str(exc),
                            )
                        )
                        if not future.done():
                            future.set_exception(exc)

        except Exception as exc:
            logger.exception("ACP session loop crashed for %s", self._artifact_key.value)
            crash_exc = exc
            self._bus.publish(
                NodeErrorEvent(
                    session_key=self._artifact_key.value,
                    artifact_key=self._artifact_key.value,
                    message=str(exc),
                )
            )
        finally:
            if crash_exc is not None:
                while True:
                    try:
                        content, future = self._queue.get_nowait()
                    except asyncio.QueueEmpty:
                        break
                    if content is not None and not future.done():
                        future.set_exception(crash_exc)

    async def _do_prompt(
        self,
        conn: Any,
        session_id: str,
        content: list,
    ) -> tuple[list[str], str]:
        await conn.prompt(content, session_id)
        results = self._buf.flush_windows(session_id)

        if _is_compacting(results):
            logger.info("Compaction detected for %s — waiting 30s", self._artifact_key.value)
            self._bus.publish(
                CompactionEvent(
                    session_key=self._artifact_key.value,
                    artifact_key=self._artifact_key.value,
                    message=f"Session {self._artifact_key.value} compacting — re-issuing after 30s",
                )
            )
            await asyncio.sleep(30)
            await conn.prompt(content, session_id)
            results = self._buf.flush_windows(session_id)

            if _is_compacting(results):
                logger.info("Still compacting for %s — resetting session", self._artifact_key.value)
                self._bus.publish(
                    ChatSessionResetEvent(
                        session_key=self._artifact_key.value,
                        artifact_key=self._artifact_key.value,
                    )
                )
                new_session = await conn.new_session(
                    cwd=self._cfg.cwd,
                    mcp_servers=self._cfg.mcp_servers or None,
                )
                session_id = new_session.session_id
                await conn.prompt(content, session_id)
                results = self._buf.flush_windows(session_id)

        return results, session_id


def _is_compacting(results: list[str]) -> bool:
    last = next((text for text in reversed(results) if text.strip()), None)
    if last is None:
        return False
    return bool(_COMPACTION_RE.search(last)) and "{" not in last


class AcpSessionPool:
    """Semaphore-gated pool of one-use ACP sessions."""

    def __init__(
        self,
        routing_key: str,
        config: SessionConfig,
        event_bus: EventPublisher[BaseEvent],
    ) -> None:
        self._routing_key = routing_key
        self._cfg = config
        self._bus = event_bus
        self._semaphore = asyncio.Semaphore(config.max_pool_size)

    @asynccontextmanager
    async def acquire(self, artifact_key: ArtifactKey) -> AsyncIterator[AcpSessionContext]:
        async with self._semaphore:
            session = await self._create_session(artifact_key)
            try:
                yield session
            finally:
                await session.stop()

    async def shutdown(self) -> None:
        return None

    async def _create_session(self, artifact_key: ArtifactKey) -> AcpSessionContext:
        ctx = AcpSessionContext(
            artifact_key=artifact_key,
            config=self._cfg,
            event_bus=self._bus,
        )
        await ctx.start()
        logger.debug("Created one-use ACP session %s", artifact_key.value)
        return ctx


class AcpSessionManager:
    """Owns one session pool per routing key."""

    def __init__(self, event_bus: EventPublisher[BaseEvent]) -> None:
        self._bus = event_bus
        self._pools: dict[str, AcpSessionPool] = {}

    def get_pool(self, routing_key: str, config: SessionConfig) -> AcpSessionPool:
        if routing_key not in self._pools:
            self._pools[routing_key] = AcpSessionPool(
                routing_key=routing_key,
                config=config,
                event_bus=self._bus,
            )
        return self._pools[routing_key]

    async def shutdown_all(self) -> None:
        for pool in self._pools.values():
            await pool.shutdown()
        self._pools.clear()
