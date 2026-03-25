The skills decided to be loaded should be offered to be determined by the controller or an LLM call 
– and this can happen as a permission gate - however we should probably use a structured permission request and 
response - see interrupt-request-gate-types.md for this - because it's important information, deciding which
skills to use for a task. 

This allows for us to pull out a lot of stuff in the future - for example if we have quite a few of them, we can 
train an embedding model. Then, we could potentially have a huge number of skills to search through by a smaller 
model, not killing the context. 

Moreover, the agent may load different pieces of the skill into the context, highlighting important passages, providing
a synopsis as it relates to this particular ticket, and referencing possible ones.

An agentic approach to progressive disclosure using a skill model - and then extracting these especially for 
retrieval or fine tuning. 

So if each agent needs one of these, and there are 12-15 agents, then we only have to run 20-30 in order to fine tune
the model, to get a much smaller model.