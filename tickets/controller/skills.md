Considering the AI typically resists it's python, we still like the quick-actions though.

So probably better to add the quick actions as endpoints themselves and just use swagger for self-describing.

And adding the documentation in the swagger annotations.

So we can definitely add/keep a python for 

1. finding repo, cloning repo, keep it for deploying (these are the ones that are needed)

Which will stay in the outer skill...

calling of the endpoints, or keeping a schema for them, seems silly if we can just get it from swagger.

Maybe there are a few that can still be used, such as graph endpoint, however mostly we will be adding this as a tool
for adding the propagators/filters/transformers for these endpoints so that an AI can register python scripts that 
do the job of filtering the result.

So I think ultimately for the controller, we swap out quick actions for an MCP tool which we can add propagators and 
transformers, and then we provide a mechanism for searching through swagger (i.e. providing the swagger endpoint and
a curl tool) - and what was quick actions and python scripts, is now MCP tools for which we attach our propagators and
filters to (that are now python/AI).

Moreover, we may consider filtering the swagger - to remove endpoints that are not to be used or something for 
different levels?

And then the AI of course has access to the swagger.json and can search through anything this way easily.

And I think we will have these skills

-> mostly the skills become repositories for information about the workflow itself, i.e. 60 second poll, this is
   how we do that, and references to various infrastructure stuff, and what components to be focusing on
-> and then maybe descriptors/instructions for how to run particular validation workflows, such as reminder to validate
   that such and such was saved to the database -> or how to update a particular validation workflow to add


1. multi agent ide -> this is the skill that contains information for both of the skills (sort of it's inherited by 
them - it has info about prompts, architecture, etc, and swagger, info about prompt/event filters
2. controller skill -> MCP skill for controller-specific endpoints w/ decorators (i.e. wraps some endpoint with 
the propagators/filters) + how to deploy/poll/manage a workflow agent
-> for this one, the MCP endpoints should probably not be endpoints anymore - it's simpler this way, don't have to 
worry about session handling by the AI or anything + layer is implicit instead of explicit
3. controller supervisor skill -> deploy/clone + MCP skill for endpoint decorators + polling from an LLM of multiple
controllers -> how to deploy/poll/manage multiple controllers
-> similar, MCP provides separate endpoints for multiple controllers, managing controllers now
4. benchmark skill
-> contains some number of tasks/goals to be completed for testing changes -> highest level of validations

