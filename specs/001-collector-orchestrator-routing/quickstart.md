# Quickstart: Collector Orchestrator Routing

## Goal

Add collector routing decisions with optional human review gates across discovery, planning, ticket, and orchestrator collectors.

## Local Validation

1. Update collector nodes and routing logic in `multi_agent_ide/src/main/java/com/hayden/multiagentide/`.
2. Ensure collector nodes record collected child statuses and routing decisions.
3. Verify the event-driven flow in `AgentRunner` and `AgentEventListener` routes collectors to orchestrators.
4. Run targeted tests:
   - Unit tests: `./gradlew :multi_agent_ide:test`
   - Integration tests (feature files): `./gradlew :test_graph:test`

## Expected Outcomes

- Collector completion triggers routing decision handling.
- Review gate can pause routing and apply operator decisions.
- No phase bypass; routing goes through orchestrators.

## Review Gate Notes

- Collector review gating is enabled via collector node metadata (`collector_review_gate=true`).
- Review decisions should specify `advance`, `rerun`, or `stop`.
