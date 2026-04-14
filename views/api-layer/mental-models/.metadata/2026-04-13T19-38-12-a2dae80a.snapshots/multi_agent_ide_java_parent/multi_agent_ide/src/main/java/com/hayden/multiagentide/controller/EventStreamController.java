package com.hayden.multiagentide.controller;

import com.hayden.multiagentide.adapter.SseEventAdapter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Event Stream", description = "Global SSE stream for all graph events")
public class EventStreamController {

    private static final String CONTROLLER_ID = "EventStreamController";

    private final SseEventAdapter sseEventAdapter;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream all graph events via SSE")
    public SseEmitter streamEvents() {
        return sseEventAdapter.registerEmitter(event -> true, CONTROLLER_ID);
    }
}
