
The stop case in the CollectorBranches in the AgentInterfaces should route to the OrchestratorCollector instead of just throwing a RuntimeException.

The interrupts should be tested.

The mock workflow should be translated to the WorkflowAgentIntegrationTest, with a focus on making sure the interrupts work, and making sure the sub-agents work. Focus on one single agent workflow, and this time we can simply load up the responses we expect to see, and then validate using a spy that it's in the correct order - you can see the current tests are passing.
