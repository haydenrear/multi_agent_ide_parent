Routing back is a bit of an issue, especially when it results in degeneracy.

So we first require human approval (or supervisor approval, really) and review of route back. However, one thing that we should probably do, in addition to this, is for, when there exists a route back loop, and then back to where we routed from, or starting the loop again, some form of collapsing of the goal and route back history into the goal.

This helps because it removes the degeneracy of the route back loop.

And more clearly, there exists a history, requests, collectors, orchestrators, agents, they are sub-graphs, and then the route back exists within these. And there exists a hierarchy within this. And so we can then ask summaries to be produced across levels of the hierarchy, in a sort of way, not to necessarily collapse the context, but to provide a mechanism for continuing convergence of the goal and refining of the task boundary.

In other words, by making the task definition and goal hierarchical in sub-graphs, we can better prune and search to enable convergence.

And of course, the process by which we prune this is agentic. 

Agentic + n where n is hierarchical context in sub-graphs.

Additionally, blackboards (artifact trees) should connect in a graph structure. So this part becomes a part of the ticketing
software because it happens across multiple executions in the database. However, artifact entity needs to connect across
tickets (like in jira) - and the CtxFs system will connect them so that it can traverse graphs of tickets. So then this
becomes another memory tool that the agents can then access to search across sessions as well.

Could try with drivine - and neo4j or try Cypher with Postgres and drivine?
