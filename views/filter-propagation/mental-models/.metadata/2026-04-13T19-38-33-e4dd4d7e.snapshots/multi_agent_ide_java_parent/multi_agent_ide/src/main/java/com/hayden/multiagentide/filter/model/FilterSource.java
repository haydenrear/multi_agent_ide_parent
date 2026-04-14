package com.hayden.multiagentide.filter.model;

import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.prompt.PromptContributor;

/**
 * Marker interface for the domain object a filter operation originates from.
 * Bindings match against this source (name/text/matchOn), not against payload aliases.
 */
public sealed interface FilterSource
        permits FilterSource.PromptContributorSource, FilterSource.GraphEventSource {

    com.hayden.acp_cdc_ai.acp.filter.FilterEnums.MatchOn matchOn();

    String matcherValue(com.hayden.acp_cdc_ai.acp.filter.FilterEnums.MatcherKey key);

    default String describe() {
        String name = matcherValue(com.hayden.acp_cdc_ai.acp.filter.FilterEnums.MatcherKey.NAME);
        return name == null || name.isBlank() ? "unknown-source" : name;
    }

    static FilterSource promptContributor(PromptContributor contributor) {
        return new PromptContributorSource(contributor);
    }

    static FilterSource graphEvent(Events.GraphEvent event) {
        return new GraphEventSource(event);
    }

    record PromptContributorSource(PromptContributor contributor) implements FilterSource {
        @Override
        public com.hayden.acp_cdc_ai.acp.filter.FilterEnums.MatchOn matchOn() {
            return com.hayden.acp_cdc_ai.acp.filter.FilterEnums.MatchOn.PROMPT_CONTRIBUTOR;
        }

        @Override
        public String matcherValue(com.hayden.acp_cdc_ai.acp.filter.FilterEnums.MatcherKey key) {
            if (contributor == null) {
                return null;
            }
            return switch (key) {
                case NAME -> contributor.name();
                case TEXT -> contributor.template();
            };
        }
    }

    record GraphEventSource(Events.GraphEvent event) implements FilterSource {
        @Override
        public com.hayden.acp_cdc_ai.acp.filter.FilterEnums.MatchOn matchOn() {
            return com.hayden.acp_cdc_ai.acp.filter.FilterEnums.MatchOn.GRAPH_EVENT;
        }

        public java.util.List<String> nameCandidates() {
            if (event == null) {
                return java.util.List.of();
            }
            java.util.LinkedHashSet<String> values = new java.util.LinkedHashSet<>();
            addIfPresent(values, event.eventType());
            Class<?> eventClass = event.getClass();
            addIfPresent(values, eventClass.getSimpleName());
            String binaryName = eventClass.getName();
            if (binaryName != null) {
                int lastDollar = binaryName.lastIndexOf('$');
                addIfPresent(values, lastDollar >= 0 ? binaryName.substring(lastDollar + 1) : binaryName);
            }
            return java.util.List.copyOf(values);
        }

        @Override
        public String matcherValue(com.hayden.acp_cdc_ai.acp.filter.FilterEnums.MatcherKey key) {
            if (event == null) {
                return null;
            }
            return switch (key) {
                case NAME -> nameCandidates().stream().findFirst().orElse(null);
                case TEXT -> {
                    String pretty = event.prettyPrint();
                    yield (pretty == null || pretty.isBlank()) ? event.eventType() : pretty;
                }
            };
        }

        private void addIfPresent(java.util.Set<String> values, String value) {
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
    }
}
