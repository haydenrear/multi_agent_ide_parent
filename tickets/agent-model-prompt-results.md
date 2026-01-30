# Prompts

## Orchestrator

### Onboarding Repository in the Orchestrator

The first thing the orchestrator should do is onboard the repository. This exists as a check to see if the repository exists in the memory, and if it does not, i.e. that there are no episodes in memory for the repository, then the commit-diff-context tool is called with rewrite and episodic memory, to get the relevant histories. However, before it starts it, it will be directed to ask the user if it should onboard with the tools it has.

Additionally, the orchestrator should check to see if the repository has the embedding/code search tools onboarded. If this repository doesn't have the repo onboarded, then it interrupts, asking the user if it should onboard with the tools it has, providing the list of onboarding tools as well.

All of these tools never need to be refreshed. For the episodic memory, we don't even save the rewritten history, we only run it through and save the episodes of memory associated with the history - and then every following commit saves it's memories as it works on them. For the other code search tools, they manage the updates.


# Results

## Discovery Results

The discovery agent results and the discovery collector results will be code-map-like results. The cool thing about these is that then there will be targets from which to train small discovery models.

Moreover, once there exist a few of them, especially with the same repo, we can try prompt tricks.

Also, we'll want references here. And these references are vitally important to the dataset generation.


### Discovery Dataset Extractions

So to steer the structured output from the discovery step:

One of the most important parts of the usage of agents is to do two things:

1. extract a small discovery LLM
2. extract a codegen retrieval dataset from the report

The discovery will be using some tooling, but ultimately will want to pass on reports, file references, and queries (that it makes up). These reports, references, and file queries will also be used to make the retrieval model better over time, and then the agentic exchange will be used to extract a smaller discovery LLM.


## Planning Results

Tickets/reports
