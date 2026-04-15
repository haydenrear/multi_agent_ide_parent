"""
Typed async event bus interfaces and default implementation.
"""
from __future__ import annotations

import asyncio
import logging
from collections.abc import Awaitable, Callable
from contextlib import suppress
from typing import Generic, Protocol, TypeVar, runtime_checkable

logger = logging.getLogger(__name__)

EventT = TypeVar("EventT")
EventListener = Callable[[EventT], Awaitable[None]]
_STOP = object()


@runtime_checkable
class EventPublisher(Protocol[EventT]):
    def publish(self, event: EventT) -> None:
        """Publish one event."""


@runtime_checkable
class EventBus(EventPublisher[EventT], Protocol[EventT]):
    def add_listener(self, listener: EventListener[EventT]) -> None:
        """Register an async listener."""

    def remove_listener(self, listener: EventListener[EventT]) -> None:
        """Remove a previously registered listener."""

    async def start(self) -> None:
        """Start draining published events."""

    async def join(self) -> None:
        """Wait until the current queue has been drained."""

    async def stop(self) -> None:
        """Drain remaining events and stop the background task."""


class AsyncEventBus(Generic[EventT]):
    """
    Queue-backed event bus with deterministic draining.

    `publish()` auto-starts the drain task when called from a running loop,
    so callers can depend only on the `EventPublisher` interface in hot paths
    while tests and apps retain explicit lifecycle control through `join()`
    and `stop()`.
    """

    def __init__(self, task_name: str = "acp-event-bus-drain") -> None:
        self._listeners: list[EventListener[EventT]] = []
        self._queue: asyncio.Queue[EventT | object] = asyncio.Queue()
        self._drain_task: asyncio.Task[None] | None = None
        self._task_name = task_name

    @property
    def listeners(self) -> tuple[EventListener[EventT], ...]:
        return tuple(self._listeners)

    @property
    def is_running(self) -> bool:
        return self._drain_task is not None and not self._drain_task.done()

    def add_listener(self, listener: EventListener[EventT]) -> None:
        if listener not in self._listeners:
            self._listeners.append(listener)

    def remove_listener(self, listener: EventListener[EventT]) -> None:
        with suppress(ValueError):
            self._listeners.remove(listener)

    def publish(self, event: EventT) -> None:
        self._ensure_drain_task()
        self._queue.put_nowait(event)

    async def start(self) -> None:
        self._ensure_drain_task()
        if self._drain_task is None:
            raise RuntimeError("AsyncEventBus.start() requires a running asyncio event loop.")

    async def join(self) -> None:
        self._ensure_drain_task()
        if self._drain_task is None and not self._queue.empty():
            raise RuntimeError("AsyncEventBus has queued events but no running drain task.")
        await self._queue.join()

    async def stop(self) -> None:
        task = self._drain_task
        if task is None:
            return

        await self.join()
        self._queue.put_nowait(_STOP)
        try:
            await task
        finally:
            self._drain_task = None

    def _ensure_drain_task(self) -> None:
        if self._drain_task is not None:
            return
        try:
            loop = asyncio.get_running_loop()
        except RuntimeError:
            return
        self._drain_task = loop.create_task(self._drain(), name=self._task_name)

    async def _drain(self) -> None:
        while True:
            item = await self._queue.get()
            try:
                if item is _STOP:
                    return
                event = item
                for listener in tuple(self._listeners):
                    try:
                        await listener(event)
                    except Exception:
                        logger.exception(
                            "ACP event listener %s raised for %s",
                            getattr(listener, "__name__", repr(listener)),
                            type(event).__name__,
                        )
            finally:
                self._queue.task_done()
