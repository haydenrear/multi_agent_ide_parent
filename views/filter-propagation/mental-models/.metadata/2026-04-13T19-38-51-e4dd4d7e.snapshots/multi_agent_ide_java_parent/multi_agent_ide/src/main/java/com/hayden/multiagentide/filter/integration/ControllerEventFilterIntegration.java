package com.hayden.multiagentide.filter.integration;

import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.filter.service.FilterExecutionService;
import com.hayden.multiagentide.filter.config.FilterContextFactory;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentide.filter.model.FilterSource;
import com.hayden.multiagentide.filter.model.layer.GraphEventObjectContext;
import com.hayden.multiagentide.filter.service.FilterDescriptor;
import com.hayden.multiagentide.filter.service.FilterResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

/**
 * Hooks controller event filtering into event-processing path before user-facing emission.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ControllerEventFilterIntegration {

    private final FilterExecutionService filterExecutionService;
    private final FilterContextFactory filterContextFactory;

    /**
     * Apply active GraphEvent path/AI filters for the given layer before serialization.
     *
     * @param layerId the layer context
     * @param event the GraphEvent payload
     * @return filtered event object (or original if no filters match), null if event should be dropped
     */
    public FilterResult<Events.GraphEvent> applyFilters(String layerId, Events.GraphEvent event) {
        if (event == null) {
            return null;
        }
        try {
            return filterExecutionService.executeFilters(
                    layerId,
                    event,
                    filterContextFactory.get(() -> new GraphEventObjectContext(layerId, event)),
                    FilterSource.graphEvent(event),
                    EnumSet.of(
                            FilterEnums.FilterKind.JSON_PATH,
                            FilterEnums.FilterKind.REGEX_PATH,
                            FilterEnums.FilterKind.AI_PATH
                    )
            );
        } catch (Exception e) {
            log.error("Controller event filter failed for layer={}, event={}", layerId, event.getClass().getSimpleName(), e);
            return new FilterResult<>(event, new FilterDescriptor.NoOpFilterDescriptor());
        }
    }
}
