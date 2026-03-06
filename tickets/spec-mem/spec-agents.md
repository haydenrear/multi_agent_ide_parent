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

---

So in this particular case, we're going to keep this idea, however, we're going to do it a bit differently to avoid
some of the technical hurdles from postgres branching.

In particular, every collector agent will perform a synchronization action over the mental model. This synchronization 
action will in fact take a postgres global lock over the mental model table. It will accept some arbitrary mental model
descriptors provided by the agents (they will have started with any existing mental model, if any exists, and then 
rewritten the mental model with their changes), and reflect on these mental models, and then "merge" them by 
overwriting the original one that these agents based them on, or writing this original if it didn't exist.

So in this way we get a "branch-merge" activity, safe and concurrent, without having to do any branching of postgres.

So this works because in the end, after the memory model is completed, we ask our collector to then return the full 
mental model as one of it's outputs. And then, having saved it, we keep this in a special place, adding it to our 
code mental model so that when we perform these workflows we can then offer it to the agents as the mental
model from which to create their mental model.

However, there is a bit of an issue, which is that the merge operation needs to be with all branched. So what we do is
we have a table, PendingMentalModels, which we save to. And then, when a single collector is ready to perform the merge,
then all PendingMentalModels get merged into the single mental model, then the collector takes the lock, reads the
single source of truth mental model, reads all pending mental models, and performs the merge.

So of course each time an agent starts, to add to it's context, it has to take that lock before branching the mental
model, then it just reads it, releases the lock (it just wants to get the latest one, and waits if it's in 
process).

