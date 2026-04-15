package com.hayden.acp_cdc_ai.acp.filter;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.hayden.acp_cdc_ai.acp.filter.path.Path;
import lombok.Builder;

/**
 * Sealed instruction type for path-based mutations.
 * Operations are applied in ascending order.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "op")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Instruction.Replace.class, name = "REPLACE"),
        @JsonSubTypes.Type(value = Instruction.Set.class, name = "SET"),
        @JsonSubTypes.Type(value = Instruction.Remove.class, name = "REMOVE"),
        @JsonSubTypes.Type(value = Instruction.ReplaceIfMatch.class, name = "REPLACE_IF_MATCH"),
        @JsonSubTypes.Type(value = Instruction.RemoveIfMatch.class, name = "REMOVE_IF_MATCH")
})
@JsonClassDescription("A polymorphic filter instruction that mutates content at a target path.")
public sealed interface Instruction
        permits Instruction.Replace, Instruction.Set, Instruction.Remove,
                Instruction.ReplaceIfMatch, Instruction.RemoveIfMatch {

    @JsonPropertyDescription("The instruction operation discriminator used for schema generation and polymorphic deserialization.")
    FilterEnums.InstructionOp op();

    @JsonPropertyDescription("The target path to mutate within the source document or object.")
    Path targetPath();

    @JsonPropertyDescription("The zero-based execution order for this instruction. Lower values run first.")
    int order();

    @Builder(toBuilder = true)
    @JsonClassDescription("Replaces the existing value at the target path with the provided value.")
    record Replace(
            @JsonPropertyDescription("The target path whose current value should be replaced.")
            Path targetPath,
            @JsonPropertyDescription("The replacement value to write at the target path.")
            Object value,
            @JsonPropertyDescription("The zero-based execution order for this instruction.")
            int order
    ) implements Instruction {
        @Override
        public FilterEnums.InstructionOp op() {
            return FilterEnums.InstructionOp.REPLACE;
        }
    }

    @Builder(toBuilder = true)
    @JsonClassDescription("Sets a value at the target path, creating it if needed when the path interpreter supports it.")
    record Set(
            @JsonPropertyDescription("The target path where the value should be set.")
            Path targetPath,
            @JsonPropertyDescription("The value to set at the target path.")
            Object value,
            @JsonPropertyDescription("The zero-based execution order for this instruction.")
            int order
    ) implements Instruction {
        @Override
        public FilterEnums.InstructionOp op() {
            return FilterEnums.InstructionOp.SET;
        }
    }

    @Builder(toBuilder = true)
    @JsonClassDescription("Removes the content addressed by the target path.")
    record Remove(
            @JsonPropertyDescription("The target path whose value should be removed.")
            Path targetPath,
            @JsonPropertyDescription("The zero-based execution order for this instruction.")
            int order
    ) implements Instruction {
        @Override
        public FilterEnums.InstructionOp op() {
            return FilterEnums.InstructionOp.REMOVE;
        }
    }

    @Builder(toBuilder = true)
    @JsonClassDescription("Replaces the value at the target path only when the current value matches the supplied matcher.")
    record ReplaceIfMatch(
            @JsonPropertyDescription("The target path whose current value may be conditionally replaced.")
            Path targetPath,
            @JsonPropertyDescription("The matcher that must match the current value before the replacement is applied.")
            InstructionMatcher matcher,
            @JsonPropertyDescription("The replacement value to write when the matcher condition succeeds.")
            Object value,
            @JsonPropertyDescription("The zero-based execution order for this instruction.")
            int order
    ) implements Instruction {
        @Override
        public FilterEnums.InstructionOp op() {
            return FilterEnums.InstructionOp.REPLACE_IF_MATCH;
        }
    }

    @Builder(toBuilder = true)
    @JsonClassDescription("Removes the value at the target path only when the current value matches the supplied matcher.")
    record RemoveIfMatch(
            @JsonPropertyDescription("The target path whose value may be conditionally removed.")
            Path targetPath,
            @JsonPropertyDescription("The matcher that must match the current value before removal is applied.")
            InstructionMatcher matcher,
            @JsonPropertyDescription("The zero-based execution order for this instruction.")
            int order
    ) implements Instruction {
        @Override
        public FilterEnums.InstructionOp op() {
            return FilterEnums.InstructionOp.REMOVE_IF_MATCH;
        }
    }
}
