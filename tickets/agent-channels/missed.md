The InterruptAddMessageCompose doesn't inject prompt contributors - it should in fact be an agent request, and then
we should be doing a prompt context decorator, request decorators, etc. As you can see route back is the same. 

Additionally, HUMAN_REVIEW -> CONTROLLER_INTERRUPT or REROUTE_INTERRUPT. And there is something in refactors about this
as well - that will handled all at once.


---

Prompts added for agent -> agent.

---

Add check-list for agent -> controller and controller -> agent for all other agents

---

Add tests for MCP tools (and probably for our MCP client), IdeMcpAsyncServer, IdeMcpServer, also want to add for LazyToolObjectRegistration, and also our invariants and surface for these ones (including MCP versions).


---

There exists a bug where if, say, the request is abandoned by the MCP client, the server (we own) in the controller 
may either
 
- still run the agent (add the message to the agent) if it's, for instance, in an end state, already produced a result
- produce other undesired results

So more clearly, when considering the abstraction of messaging agents through tools and REST controllers, 
there probably needs to be a set of clear rules and updates to the state, and state transition logic (for locking).

Envisioning this as (sort of) like a protocol where an agent can be in some set of states, and depending on the state
it is in, determines whether it can still accept a message, will help.

For example, at the moment when we receive an end token event (a message stream event with an eos token or similar) 
for a session is really the moment at which we'd want to say, now we can't do return. Because think about it, it's like
adding a message channel, it will not be used at the best.

And it can be very simple to do this, considering our event-driven architecture.