
There exists memory models, these are persistent, constantly updated, and embedded for retrieval. However, there is
still a lot of value in the spec pipeline. And in particular, in some cases, the refinement of the spec can provide
a mechanism for design that seems to be unparalleled. However, after this initial design and codegen process, the 
spec seems to be unnecessary weight.

Therefore, I'm considering creating an agentic pipeline as a sub-agent pipeline in the orchestrator onboarding.

In this case, we have

Repo onboarding first, then we have spec agents. An agent for each spec. And the result of this spec agent is then 
used to encode a provisional update to the episodic memory. So then, we branch the postgres database for the episodic
memory, we create a mental model for the spec, and then if there existed a spec agent workflow, we update the prompts
to let the following agents know.

In this way, we encode the previous mental models with the new spec and provide that mechanism to the following 
discovery, planning, and ticket agents. And if the user/steering LLM chose no spec on this, then we exclude it from the
prompt. 

And then if not, then in the final collector, we have a "spec-mem merge", whereby the new mental model is produced
that contains the spec, and then all the spec docs are burned. In this way we get the benefit of the process from the 
spec, but we don't have the heavyweight mechanism of managing the spec.