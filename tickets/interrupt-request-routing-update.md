The interrupt request routing can now be augmented with the changes to the schema.

The current issue is that the process is a bit inflexible in the following way:

when the workflow is in a state it would be hard to react explicitly to just jump to another state. For example, what happens if I send a message and say, now I want you to jump back to planning orchestrator, we missed some planning. However, the interrupt would be too flexible and the process would diverge in the case where we had one and never filtered out.

So instead, the way we can do it is have one interrupt action in WorkflowAgent, but that interrupt action gets all other in the schema filtered besides the one that route to it, in every case except for when the user sends a re-route request - then in this case the user specifies more information for the routing agent.

This is especially important for times when the user for instance needs to edit the plan, or add more discovery, or skip to ticket, or when the AI starts to degenerate. In this case, the supervisor should be able to intercept, produce an interrupt request for review, and then the interrupt resolution specifies which to route to, and the message. Then all other routes are then filtered from the schema - the agent is then forced to send to that agent.

So this is especially important for these sort of supervisor degeneration and stability guarantees. To be able to intercept execution and put it back on the correct trace is critically important.

So interestingly, the prune mechanism probably uses this same interrupt mechanism, because it's like saying, get rid of all of those nodes, and go back to here, sort of getting rid of that whole part of the execution graph.

However, branch is a bit different, what branch would do is actually start a whole new workflow agent with the exact same context, copying over the whole graph effectively and starting the agent at that point, in the case where it's branching a workflow agent, or if it's branching one of the dispatched agents, it just clones the dispatched agent.


---
