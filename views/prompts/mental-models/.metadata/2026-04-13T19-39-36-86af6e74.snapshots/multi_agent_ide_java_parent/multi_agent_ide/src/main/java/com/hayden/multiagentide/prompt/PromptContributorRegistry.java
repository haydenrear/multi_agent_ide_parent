package com.hayden.multiagentide.prompt;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PromptContributorRegistry {

    private final List<PromptContributor> contributors;

    public PromptContributorRegistry(List<PromptContributor> contributors) {
        this.contributors = contributors != null ? List.copyOf(contributors) : List.of();
        var contributorMap = this.contributors.stream()
                .filter(c -> c != null && c.name() != null)
                .collect(Collectors.toMap(
                        PromptContributor::name,
                        contributor -> contributor,
                        (existing, replacement) -> {
                            throw new IllegalArgumentException("Failed because found duplicate.");
                        }
                ));
    }

    public List<PromptContributor> getContributors(PromptContext context) {
        if (contributors.isEmpty()) {
            return List.of();
        }
        Map<String, PromptContributor> unique = contributors.stream()
                .filter(c -> c != null && c.name() != null)
                .collect(Collectors.toMap(
                        PromptContributor::name,
                        contributor -> contributor,
                        (existing, replacement) -> {
                            return existing;
                        }
                ));
        List<PromptContributor> filtered = new ArrayList<>(unique.values());
        filtered.removeIf(contributor -> !contributor.isApplicable(context));
        filtered.sort(Comparator
                .comparingInt(PromptContributor::priority)
                .thenComparing(PromptContributor::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(filtered);
    }

    public Set<PromptContributorDescriptor> getDescriptors() {
        return contributors.stream()
                .filter(c -> c != null && c.name() != null)
                .map(PromptContributor::descriptor)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
