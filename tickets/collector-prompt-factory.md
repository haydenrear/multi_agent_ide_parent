Each of the agents that is to be returning a result implementing ConsolidationTemplate should make sure and ask the AI
specifically to consolidate the work of the dispatched agents into the respective UpstreamContext.

```java
@Builder(toBuilder=true)
@JsonClassDescription("Consolidated discovery results and routing decision.")
@With
record DiscoveryCollectorResult(
        @JsonPropertyDescription("Unique context id for this result.")
        @SkipPropertyFilter
        ArtifactKey contextId,
        @JsonPropertyDescription("Unified consolidated output summary.")
        String consolidatedOutput,
        @JsonPropertyDescription("Collector decision for routing.")
        CollectorDecision collectorDecision,
        @JsonPropertyDescription("Additional metadata for the result.")
        Map<String, String> metadata,
        @JsonPropertyDescription("Unified code map derived from discovery.")
        CodeMap unifiedCodeMap,
        @JsonPropertyDescription("Recommendations derived from discovery.")
        List<Recommendation> recommendations,
        @JsonPropertyDescription("Query-specific findings keyed by query name.")
        Map<String, QueryFindings> querySpecificFindings,
        @JsonPropertyDescription("Curated discovery context for downstream agents.")
        UpstreamContext.DiscoveryCollectorContext discoveryCollectorContext
) implements ConsolidationTemplate, AgentResult {
}
```

In this case it's UpstreamContext.DiscoveryCollectorContext.

It could also be better to simply intercept the result after, and load this with all agent results. However, we already
add all context using the prompt contributor, so it's better to think of this as a structured output for the discovery
collector.

Additionally, we have to double check that we're using that prompt contributor that adds all context to all of our agents.
In particular, it will be best to update the blackboard history after we populate the request with it's enrichment data.
We can do a "replaceLast" or only add to the blackboard once we populate with request enrichment. Then, we add requests 
also with all previous contexts and goals to the CurationHistoryPromptContributorFactory. The reason being that we don't
have the history of the goals currently there, so we add that, as well as binders between them. Then we make sure and
add a bit in context of current request - it saying

Now, you are in this phase. That means you have the following options ... and then it gets a bit more clear understanding
of where it is, the goal it had, the goal it has, etc. This could be good to merge this with we are here prompt 
contributor - or potentially we can append that together with this one after it?