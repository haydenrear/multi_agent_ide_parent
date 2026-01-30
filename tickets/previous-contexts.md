The previous contexts need to be added using toBuilder for each of the items. This can happen in the BlackboardHistory,
when we call registerAndHideInput - and then we'll match over the input request, and use toBuilder to set the previous
if it exists. So we'll tell these agents to skip setting the PreviousContext and we'll set that manually. 

There are some of these for which the previous could mean multiple things, specifically the agents that are dispatched,
such as the TicketAgent, the PlanningAgent, not the TicketAgents, but the TicketAgent - the agents that are being 
dispatched in sub-agents. For these ones, instead of setting PreviousContext manually like we're doing, we'll instead
rely on the dispatching agent to produce the request. So for this, we'll just make sure that PreviousContext is referenced
in the prompt for this, and that it tells the agent to specify.

So for the setting of the previous context on the various agent models, I'd like to pull this out into a separate class, 
RequestEnrichment, and perform this, among the other request enrichment happening in AgentInterfaces in a single place.
In this request enrichment class there will also be a PreviousContextFactory, which will build the previous context where
possible that it can be set.

Additionally, for all the agents that have a PreviousContext, these need to be typed out - there is a previous context 
for each agent, that's built in this request enrichment class, as you can see, the previous person forgot to set the 
types, and it just says PreviousContext.

Additionally, we have to set the ContextId for all the requests and all creation of all the agent models.

**Completed**
