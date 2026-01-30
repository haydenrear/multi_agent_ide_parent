add the permission request/response to sse emitter/rest **Completed**

add a cancellation timeout - and then add a thread that checks all pending and clears that which exceeds the timeout,
and expose the timeout as well as a parameter with a default value. **Pending**

ensure emission of any interrupt resolved event, or created event, as an event in the parent node, and filter out so  
that it does not create new nodes, only appends to the associated node, and only serializes in the parent node chat. 
Currently in the frontend it adds it as a separate agent node, but it needs to emit in the node for which the agent
routed to the interrupt action. Otherwise the frontend will just fill up with interrupt nodes and you won't know which
LLM call emitted it. **Pending**
