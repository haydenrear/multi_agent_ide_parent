Note: a lot of the current python will be replaced with either swagger and MCP tools, and much of the current will be
captured with notes about workflows -> make it more flexible, more powerful

Considering the AI typically resists it's python, we still like the quick-actions though.

So probably better to add the quick actions as endpoints themselves and just use swagger for self-describing.

And adding the documentation in the swagger annotations.

So we can definitely add/keep a python for 

1. finding repo, cloning repo, keep it for deploying (these are the ones that are needed)
2. policy/propagater python exists

The reason being the AI often fails to do the tmp thing, no matter how many times I tell it, or make updates to it. 
Additionally, the deploy script is often used - however we need to update it so that it saves the build-log to the 
root of the repo and makes it more clear when it fails. 

And for the policy/propagator, the AI is constantly recreating python scripts for parsing it. And it will be cheaper
to enable/disable policy and more ergonomic, and better for when we pull out into a smaller model with OOD, because 
it will be more easy to distill it. I think the way that the propagator will work is there will be several saved:

1. logs (propagating log information) - the best AI often doesn't see basic log information because it's not looking 
deep enough or we have to ask it explicitly
2. calling the policy endpoint produces many output files to be searched through by the AI

Finding repo, cloning repo, deploy will stay in the outer skill.
Then the registration of policy is in a shared skill.

Calling of the endpoints, or keeping a schema for them, seems silly if we can just get it from swagger.

So we swap out quick actions kept (i.e. graph endpoint) for an MCP tool which we can decorate with propagators and 
transformers using session id injected, and then we provide a mechanism for searching through swagger (i.e. providing 
the swagger endpoint and a curl tool) - and what was quick actions and python scripts, is now MCP tools for which 
we attach our propagators and filters to (that are now python/AI).

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

