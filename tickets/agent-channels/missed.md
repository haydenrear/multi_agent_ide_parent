The InterruptAddMessageCompose doesn't inject prompt contributors - it should in fact be an agent request, and then
we should be doing a prompt context decorator, request decorators, etc. As you can see route back is the same. 

Additionally, HUMAN_REVIEW -> CONTROLLER_INTERRUPT or REROUTE_INTERRUPT. And there is something in refactors about this
as well - that will handled all at once.


---

Prompts added for agent -> agent.

---

Add check-list for agent -> controller and controller -> agent for all other agents

---

Add tests for MCP tools (and probably for our MCP client),
IdeMcpAsyncServer, IdeMcpServer
