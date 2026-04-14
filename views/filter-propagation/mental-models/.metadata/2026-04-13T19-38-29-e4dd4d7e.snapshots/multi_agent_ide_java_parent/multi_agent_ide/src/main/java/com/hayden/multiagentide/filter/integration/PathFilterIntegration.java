package com.hayden.multiagentide.filter.integration;

import com.hayden.multiagentide.filter.service.FilterExecutionService;
import com.hayden.multiagentide.filter.config.FilterContextFactory;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentide.filter.model.FilterSource;
import com.hayden.multiagentide.filter.model.layer.FilterContext;
import com.hayden.multiagentide.filter.service.FilterDescriptor;
import com.hayden.multiagentide.filter.service.FilterResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Hooks PathFilter execution with interpreter dispatch into applicable data paths.
 * Supports JSON path, markdown path, and regex path filter types.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PathFilterIntegration {

    private final FilterExecutionService filterExecutionService;

    private final FilterContextFactory filterContextFactory;

    public FilterResult<String> applyTextPathFilters(String layerId, FilterSource source, String content,
                                                     FilterContext.PathFilterContext pathFilterContext) {
        return applyFilters(layerId, source, content, EnumSet.of(FilterEnums.FilterKind.MARKDOWN_PATH, FilterEnums.FilterKind.REGEX_PATH, FilterEnums.FilterKind.AI_PATH),
                filterContextFactory.get(() -> pathFilterContext));
    }

    public FilterResult<String> applyJsonPathFilters(String layerId, FilterSource source, String content,
                                                     FilterContext.PathFilterContext pathFilterContext) {
        return applyFilters(layerId, source, content, EnumSet.of(FilterEnums.FilterKind.JSON_PATH, FilterEnums.FilterKind.REGEX_PATH, FilterEnums.FilterKind.AI_PATH),
                filterContextFactory.get(() -> pathFilterContext));
    }

    public FilterResult<String> applyFilters(String layerId, FilterSource source, String content, Set<FilterEnums.FilterKind> allowedKinds,
                                             FilterContext.PathFilterContext pathFilterContext) {
        try {
            return filterExecutionService.executeFilters(
                    layerId,
                    content,
                    filterContextFactory.get(() -> pathFilterContext),
                    source,
                    allowedKinds
            );
        } catch (Exception e) {
            log.error("Path filter failed for layer={}, source={}", layerId, source == null ? "none" : source.describe(), e);
            return new FilterResult<>(content, new FilterDescriptor.NoOpFilterDescriptor());
        }
    }
}
