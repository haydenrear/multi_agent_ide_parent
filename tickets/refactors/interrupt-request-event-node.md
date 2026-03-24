If we hear an interrupt request event, then save an InterruptTransitionToAgentNode, a GraphNode, and then search for that
in PermissionGateService instead of the event.

This is more ergonomic, and there should be a node in the graph there.