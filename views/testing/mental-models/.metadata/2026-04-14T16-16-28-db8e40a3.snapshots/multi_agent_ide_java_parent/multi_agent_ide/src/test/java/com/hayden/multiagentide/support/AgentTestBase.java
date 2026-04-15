package com.hayden.multiagentide.support;

import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for agent tests.
 * 
 * Test strategies:
 * 
 * 1. For workflow routing tests: Mock the WorkflowAgent action methods directly
 *    to return specific routing responses, then verify the planner routes correctly
 *    through the graph.
 * 
 * 2. For ACP integration tests: Mock AcpChatModel to return specific responses,
 *    then verify the actions process them correctly and return proper routing.
 * 
 * DO NOT mock LlmOperations - it's just an intermediary. Mock either above it
 * (actions) or below it (AcpChatModel).
 */
@ActiveProfiles({"testdocker", "test"})
public abstract class AgentTestBase {
    // Common test utilities can go here
}
