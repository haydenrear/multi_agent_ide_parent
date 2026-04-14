package com.hayden.multiagentide.agent;

import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.multiagentide.events.DegenerateLoopException;

import java.util.Optional;

public interface DegenerateLoopPolicy {

    Optional<DegenerateLoopException> detectLoop(BlackboardHistory history, String actionName, Artifact.AgentModel input);
}
