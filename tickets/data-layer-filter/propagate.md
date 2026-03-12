
So this existing infrastructure we built for this capturing these records, everything like that. Now we have everything
for communications running through these functions. 


So in a similar manner that we were filtering, now we add levels of importance/notifications. These same layers that
we have, now they register importance notifications, and this is our propagation across the hierarchy.

Similarly, now, we add various forms of them, to mirror our current. We add an AI summarization propagation operator,
for instance, we add matching with them, etc.

So in this way, we have sort of messenger operators that propagate important information across the organizational 
boundary, just as we add filters.

---

So for this one, I'd also like to introduce a way for mapping to happen for controller endpoints more generally.

In other words, there exists WorkflowGraphResponse in LlmDebugUiController

and what I'd like to do in propagate is have various interceptors and mappers for the propagate, in addition to being
able to add filters for this.

So then, controller endpoints are consistently going to be adhering to some json schema on the output, for sanity,
but wanting to be a serialized string that can be intercepted and propagated in some arbitrary different schema 
downstream, and certainly we shouldn't just fail because of it.

This comes from the empirical result we see when the controller is being used, where the AI constantly writes it out
it's self. So in the best case, the AI uses it's best result, and evolves it, so that the small models can have 
something to start with. Additionally, these outputs are to be captured and saved so that we understand how various
propagators affect downstream performance.

So mostly this can happen client-side as the controller agent moves to the java code and deployment happens in the 
controller supervisor skill.


---

After thinking bout this for some time, I like the idea of adding this more as an aspect or interceptor. You run the 
data through the session, saying only things like (does this look out of domain ?) and then provide the call_controller
under escalate.

---

In particular, it seems that the controller layer often has difficulty filtering through the information from the agents to check the status. We tried to fix this with quick-actions. However, the input often gets cut off. So we should consider this the primary mechanism for propagator. Whereby important information needs to be approved and opportunities to intercept are provided.

For example, discovery collector, finish of discovery agent, and collector and agent, should have propagators for approval.
