# Commit Updates

The prompt for all the agents should provide a trailer. The commit agent is sometimes being called after
the agent already does the commit. 

In that case, the commit message doesn't have the trailer, because it's not included. So the commit agent's trailer 
should be passed into the agent's prompt as well.

# Required Review

Review is propagated ... that's the OOD idea. We add it as a cross-cutting concern - it's detected to be
needed, then propagated, and rerouted.