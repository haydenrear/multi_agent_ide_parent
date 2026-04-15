package com.hayden.acp_cdc_ai.acp.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * JSON helpers for serializing instruction lists with their polymorphic type discriminator.
 */
public final class InstructionJson {

    private static final TypeReference<List<Instruction>> INSTRUCTION_LIST_TYPE = new TypeReference<>() { };

    private InstructionJson() {
    }

    public static String toJsonSafely(ObjectMapper objectMapper, List<Instruction> instructions) {
        if (instructions == null) {
            return null;
        }
        try {
            return objectMapper.writerFor(INSTRUCTION_LIST_TYPE).writeValueAsString(instructions);
        } catch (Exception e) {
            try {
                return objectMapper.writeValueAsString(String.valueOf(instructions));
            } catch (Exception ignored) {
                return String.valueOf(instructions);
            }
        }
    }
}
