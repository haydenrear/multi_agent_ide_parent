Call agent is sort of it's own vision:

each agent is an intelligence and a context. So we should find a way for the agent's to reference, injecting an agent ref is like saying, call this agent for more specifics about this piece of context. We can replace a lot of stuff in the history with this.

Moreover, I'm thinking we add AgentRef to each agent request and agent result, and then when performing the serialization for other agents (other agent serialization ctx, for instance passing in the serialization ctx a particular agent type), then other agent's stuff will have a ref to the tool call (saying, if you really need more information here, this is the person to call) .
