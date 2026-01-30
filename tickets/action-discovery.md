
Actions in a2uiMessageBuilder.ts are discrete number of actions. However, we can have an action model which we map
actions to, and then make that discoverable from our APIs, so then at startup of MCP server the server discovers all
actions by calling some number of action endpoints, a2a agents, etc, then maps to action schemas, and then include
that in the gui event schema. This way, the model can provide buttons to do cool stuff to the user.

So then we'll just make the request functions a map and at boot we call some discovery servers to register them, 
replacing what we've done in a2uiRegistry. Additionally, we'll have to send the whole context for each action, 
so it'll be an ActionRequest, with associated name, context, and IDs.