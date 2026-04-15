package com.hayden.acp_cdc_ai.sandbox;


import com.hayden.acp_cdc_ai.acp.config.AcpChatOptionsString;
import com.hayden.acp_cdc_ai.acp.config.AcpResolvedCall;
import com.hayden.acp_cdc_ai.repository.RequestContext;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;

public interface SandboxTranslationStrategy {

    Logger log = LoggerFactory.getLogger(SandboxTranslationStrategy.class);

    static AcpArgs parseFromAcpArgsCodex(List<String> acpArgs, String modelName) {
        return parseFromAcpArgs(acpArgs, modelName, (k, v) -> {
            List<String> a = new ArrayList<>();
            a.add("-c");
            a.add("%s=%s".formatted(k, v));
            return a;
        });
    }

    record AcpArgs(List<String> args, @Nullable String model) {
        static AcpArgs empty(){
            return new AcpArgs(new ArrayList<>(), null);
        }
    }

    static AcpArgs parseFromAcpArgsClaude(List<String> acpArgs, String modelName) {
        return parseFromAcpArgs(acpArgs, modelName, (k, v) -> {
            List<String> a = new ArrayList<>();
            a.add("--" + k);
            a.add(v);
            return a;
        });
    }

    static AcpArgs parseFromAcpArgs(
            List<String> acpArgs,
            String modelOverride,
            BiFunction<String, String, List<String>> argParser
    ) {
        List<String> args = new ArrayList<>();
        if (acpArgs.isEmpty()) return AcpArgs.empty();

        if (acpArgs.size() % 2 != 0) {
            log.error("Acp args not in valid format.");
            return AcpArgs.empty();
        }

        String model = null;
        boolean sawModel = false;

        for (int i = 0; i < acpArgs.size() - 1; i += 2) {
            String flag = acpArgs.get(i);
            String value = acpArgs.get(i + 1);

            if (!flag.startsWith("--")) {
                log.error("Arg was not in valid format: {}.", flag);
                continue;
            }

            String arg = flag.substring(2).trim(); // avoid regex replaceFirst

            if (Objects.equals(arg, "model")) {
                sawModel = true;
                String effective = Objects.equals(modelOverride, AcpChatOptionsString.DEFAULT_MODEL_NAME)
                        ? value
                        : modelOverride;

                args.addAll(argParser.apply("model", effective));
                model = effective;
                continue;
            }

            args.addAll(argParser.apply(arg, value));
        }

        // If no --model was provided, but an override was supplied, inject it once.
        if (!sawModel && !Objects.equals(modelOverride, AcpChatOptionsString.DEFAULT_MODEL_NAME)) {
            args.addAll(argParser.apply("model", modelOverride));
        }

        return new AcpArgs(args, model);
    }

    String providerKey();

    SandboxTranslation translate(RequestContext context, List<String> args, String modelName);

    default SandboxTranslation translate(RequestContext context, List<String> args) {
        return translateResolvedCall(context, args, null);
    }

    default SandboxTranslation translateResolvedCall(RequestContext context, List<String> args, @Nullable AcpResolvedCall acpResolvedCall) {
        var model = Optional.ofNullable(acpResolvedCall)
                .flatMap(a -> Optional.ofNullable(a.effectiveModel()))
                .filter(StringUtils::isNotBlank)
                .orElse(AcpChatOptionsString.DEFAULT_MODEL_NAME);
        return translate(context, args, model);
    }
}
