skill for controller supervisor.

Controller agent

```
I’m working on my multi_agent_ide_java_parent project - from GitHub. And I’m interested in adding another layer to it. In particular, I’ve been using a skill to steer it with an LLM. So the LLM has cloned, then put goals, then queried the graph, zoomed in on log, etc. 

So I’m using an event driven framework with swappable UI, so the skill just uses the REST endpoints to interact using Python. 

After the multi agent workflow is finished, typically the LLM then analyzes the results, the log, and makes recommendations for how to evolve the prompts, and then from the main repo we push and then pull from the temp, and do it again. 

In particular, I’m interested in pulling out this workflow into the following pieces

1. A controller agent that performs some of the work, such as putting to a temp repo, polling the results, and analyzing the logs afterwards. So this will be a multi agent workflow again with structured responses for each of these. Each of them is an @Action, and we use a similar mechanism to our existing planner, with routing objects.
2. Endpoints for to interact with multiple of these controller agents. Currently, the LLM polls a graph endpoint that shows the status of the workflow. So for this, the new layer will submit multiple jobs to multiple controllers, and then poll a new endpoint that returns all of the controller agents as a composite. It’s a similar mechanism, polling an endpoint that shows the workflow, except the new endpoint merges multiple workflows so the submitter can manage multiple agents it’s submitted.
3. Similar to our current workflow, we will be adding events and artifacts for the new controller agents, and then these will be then filtered by the new endpoint in our existing data infrastructure.
4. We will then add a new controller supervisor skill, which shows how to submit controller agents, and the controller agents will load the old supervisor skill to interact with the workflow agents.
5. The new controller supervisor skill and prompts are better at intercepting the workflow as it derails - this is the point at which the LLM must start to operate as an OOD detector, providing feedback to lower levels. 

So the vision here is higher leverage. We are working on extracting higher levels of leverage from the data. This is the implementation of the extraction of our agents into domain specific models through structured interactions. We start with big models to capture data, a mechanism for controller and OOD guardrails, and then we can use less tokens for bigger models as we tune smaller models from the bigger models data.

—-

A couple gotchas:

To start, the orchestrator and the workflow agents live in the same app. However the orchestrator polls using the skill through REST. 

Additionally, this will be using Embabel Agents Actions, which we already use for the workflow agents. So all of that is provided by the framework. Additionally, we have infrastructure for the keys, which are hierarchical. There is a gotcha for the keys, however. I’m introducing a new root key for every execution, and then children of that execution get the child made from that. In this new case, the controller job makes a child from its key for the workflow. And I’m thinking the LLM, for each of its sessions, will create the root key, and then for each controller job it submits with its root key, a new child will be created. This way, when it calls the endpoint it will just retrieve all children created from that root key. However, we will need to go through and make sure that we didn’t do any assertions about execution artifacts having a root key. Some of that will need to be validated.

Additionally, for now, because the controller agent exists in the same application as the workflow agents, and we intend to use this application to then continue to build this application, this means that the controller supervisor skill needs to have instructions on how to clone and deploy. So this issue only happens for using it to build itself. However, it complicates the self-improvement loop a bit, putting all log analysis still in the controller agent, and still making aggregate or composite signaling possible, and the controller can monitor multiple of the benchmarks at a time, however it cannot rebuild/redeploy itself for obvious reasons. In the long-term we can add this for building other applications, or even other components of the stack, or even some parts of the stack, such as prompt evolutions that load from files, but not for this particular case.

So for now we'll be moving the clone to tmp, build/redeploy into the controller supervisor skill out of the supervisor skill. We'll also need to add endpoints for not only composite graph but also for posting multiple goals, and this multiple goal endpoint is the one that goes through the controller agent. Each controller agent only runs one workflow agent, and polls it using the same endpoints. The LLM, outside the app loads the controller supervisor skill, and calls the composite endpoints, polling. Each controller polls the graph endpoints for only it's workflow agent. Each controller, after finish of it's workflow agent, does log analysis and provides feedback for LLM (with controller supervisor skill for redeploy), which LLM with controller supervisor skill (outside of app) can do in aggregate by redeploying app after applying changes from all controller agents.

Additionally, we won't call it session key, we'll call it the controller's root artifact key/node ID, to keep things consistent. And we have all of that createChild on ArtifactKey already set up, along with a trie data structure for inserting artifacts from the events, that get inserted automatically based on hierarchical structure of the key.
```
