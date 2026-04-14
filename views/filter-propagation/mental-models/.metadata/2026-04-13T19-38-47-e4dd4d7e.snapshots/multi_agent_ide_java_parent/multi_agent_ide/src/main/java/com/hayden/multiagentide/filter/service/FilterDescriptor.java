package com.hayden.multiagentide.filter.service;

import com.hayden.acp_cdc_ai.acp.filter.Instruction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public sealed interface FilterDescriptor permits
        FilterDescriptor.ErrorFilterDescriptor,
        FilterDescriptor.InstructionsFilterDescriptor,
        FilterDescriptor.NoOpFilterDescriptor,
        FilterDescriptor.SerdesFilterDescriptor,
        FilterDescriptor.SimpleFilterDescriptor {

    record Entry(
            String descriptorType,
            String policyId,
            String filterId,
            String filterName,
            String filterKind,
            String sourcePath,
            String action,
            String executorType,
            Map<String, String> executorDetails,
            List<Instruction> instructions
    ) {
        public Entry {
            executorDetails = executorDetails == null ? Map.of() : Map.copyOf(executorDetails);
            instructions = instructions == null ? List.of() : List.copyOf(instructions);
        }

        public Entry withInstructions(List<Instruction> nextInstructions) {
            return new Entry(
                    descriptorType,
                    policyId,
                    filterId,
                    filterName,
                    filterKind,
                    sourcePath,
                    action,
                    executorType,
                    executorDetails,
                    nextInstructions
            );
        }
    }

    FilterDescriptor and(FilterDescriptor other);

    List<Entry> entries();

    default List<Instruction> instructions() {
        return entries().stream()
                .flatMap(e -> e.instructions().stream())
                .toList();
    }

    default List<Throwable> errors() {
        return List.of();
    }

    record NoOpFilterDescriptor()
            implements FilterDescriptor {
        @Override
        public FilterDescriptor and(FilterDescriptor other) {
            return other == null ? this : other;
        }

        @Override
        public List<Entry> entries() {
            return List.of();
        }

        @Override
        public List<Throwable> errors() {
            return List.of();
        }
    }

    record ErrorFilterDescriptor(
            Throwable error,
            List<FilterDescriptor> previous,
            Entry entry)
            implements FilterDescriptor {

        public ErrorFilterDescriptor(Throwable error, List<FilterDescriptor> previous) {
            this(error, previous, null);
        }

        @Override
        public List<Entry> entries() {
            List<Entry> result = flatten(previous);
            Entry resolved = entry == null
                    ? new Entry(
                    "ERROR",
                    null,
                    null,
                    null,
                    null,
                    null,
                    "ERROR",
                    null,
                    errorDetails(error),
                    List.of()
            )
                    : withErrorDetails(entry, error);
            result.add(resolved);
            return List.copyOf(result);
        }

        @Override
        public List<Throwable> errors() {
            List<Throwable> result = flattenErrors(previous);
            if (error != null) {
                result.add(error);
            }
            return List.copyOf(result);
        }

        @Override
        public FilterDescriptor and(FilterDescriptor other) {
            if (other == null || other instanceof NoOpFilterDescriptor) {
                return this;
            }
            return new SimpleFilterDescriptor(List.of(this, other));
        }
    }

    record InstructionsFilterDescriptor(
            List<Instruction> instructions,
            List<FilterDescriptor> previous,
            Entry entry)
            implements FilterDescriptor {
        public InstructionsFilterDescriptor(List<Instruction> instructions, List<FilterDescriptor> previous) {
            this(instructions, previous, null);
        }

        @Override
        public List<Entry> entries() {
            List<Entry> result = flatten(previous);
            Entry resolved = entry == null
                    ? new Entry("PATH", null, null, null, null, null, null, null, Map.of(), instructions)
                    : entry.withInstructions(instructions);
            result.add(resolved);
            return List.copyOf(result);
        }

        @Override
        public List<Throwable> errors() {
            return List.copyOf(flattenErrors(previous));
        }

        @Override
        public FilterDescriptor and(FilterDescriptor other) {
            if (other == null || other instanceof NoOpFilterDescriptor) {
                return this;
            }
            return new SimpleFilterDescriptor(List.of(this, other));
        }
    }

    record SimpleFilterDescriptor(
            List<FilterDescriptor> previous,
            Entry entry) implements FilterDescriptor {

        public SimpleFilterDescriptor(List<FilterDescriptor> previous) {
            this(previous, null);
        }

        @Override
        public List<Entry> entries() {
            List<Entry> result = flatten(previous);
            if (entry != null) {
                result.add(entry);
            }
            return List.copyOf(result);
        }

        @Override
        public List<Throwable> errors() {
            return List.copyOf(flattenErrors(previous));
        }

        @Override
        public FilterDescriptor and(FilterDescriptor other) {
            if (other == null || other instanceof NoOpFilterDescriptor) {
                return this;
            }

            List<FilterDescriptor> merged = new ArrayList<>();
            if (previous != null) {
                merged.addAll(previous);
            }
            if (entry != null) {
                merged.add(new SimpleFilterDescriptor(List.of(), entry));
            }
            merged.add(other);
            return new SimpleFilterDescriptor(merged);
        }
    }

    record SerdesFilterDescriptor(
            String operation,
            List<FilterDescriptor> previous,
            Entry entry) implements FilterDescriptor {

        public SerdesFilterDescriptor(String operation, List<FilterDescriptor> previous) {
            this(operation, previous, null);
        }

        @Override
        public List<Entry> entries() {
            List<Entry> result = flatten(previous);
            Entry resolved = entry == null
                    ? new Entry("SERDES", null, null, null, null, null, operation, null, Map.of(), List.of())
                    : entry;
            result.add(resolved);
            return List.copyOf(result);
        }

        @Override
        public List<Throwable> errors() {
            return List.copyOf(flattenErrors(previous));
        }

        @Override
        public FilterDescriptor and(FilterDescriptor other) {
            if (other == null || other instanceof NoOpFilterDescriptor) {
                return this;
            }
            return new SimpleFilterDescriptor(List.of(this, other));
        }
    }

    private static List<Entry> flatten(List<FilterDescriptor> descriptors) {
        if (descriptors == null || descriptors.isEmpty()) {
            return new ArrayList<>();
        }
        List<Entry> result = new ArrayList<>();
        for (FilterDescriptor descriptor : descriptors) {
            if (descriptor == null || descriptor instanceof NoOpFilterDescriptor) {
                continue;
            }
            result.addAll(Objects.requireNonNullElse(descriptor.entries(), List.of()));
        }
        return result;
    }

    private static List<Throwable> flattenErrors(List<FilterDescriptor> descriptors) {
        if (descriptors == null || descriptors.isEmpty()) {
            return new ArrayList<>();
        }
        List<Throwable> result = new ArrayList<>();
        for (FilterDescriptor descriptor : descriptors) {
            if (descriptor == null || descriptor instanceof NoOpFilterDescriptor) {
                continue;
            }
            result.addAll(Objects.requireNonNullElse(descriptor.errors(), List.of()));
        }
        return result;
    }

    private static Entry withErrorDetails(Entry entry, Throwable throwable) {
        Map<String, String> details = new java.util.LinkedHashMap<>(entry.executorDetails());
        details.putAll(errorDetails(throwable));
        return new Entry(
                entry.descriptorType(),
                entry.policyId(),
                entry.filterId(),
                entry.filterName(),
                entry.filterKind(),
                entry.sourcePath(),
                entry.action(),
                entry.executorType(),
                details,
                entry.instructions()
        );
    }

    private static Map<String, String> errorDetails(Throwable throwable) {
        if (throwable == null) {
            return Map.of();
        }
        Map<String, String> details = new java.util.LinkedHashMap<>();
        details.put("errorType", throwable.getClass().getName());
        if (throwable.getMessage() != null) {
            details.put("errorMessage", throwable.getMessage());
        }
        Throwable root = throwable;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root != throwable) {
            details.put("rootCauseType", root.getClass().getName());
            if (root.getMessage() != null) {
                details.put("rootCauseMessage", root.getMessage());
            }
        }
        return details;
    }
}
