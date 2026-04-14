package com.hayden.multiagentide.ui.shared;

import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.ui.state.UiState;
import com.hayden.multiagentide.ui.state.UiViewport;

public interface SharedUiInteractionService {

    UiState reduce(UiState state, Events.GraphEvent event, UiViewport viewport, String nodeId);

    UiStateSnapshot toSnapshot(UiState state);

    Events.UiInteractionEvent toInteractionEvent(UiActionCommand command);

    String resolveNodeId(Events.GraphEvent event, String fallbackNodeId);
}
