
One thing we were forgot to add is

ACTION_SESSION,
ALL_AVAILABLE_SESSION_W_[RESOLUTION_MODE],
SMART_SESSION_ROUTER

session mode for AiFilterTool.SESSION_MODE

In particular, when a particular agent is running and filtering it's prompts, or graph events and it's not active, 
i.e. it's already returned it's results (i.e. it's not in the middle of returning results), then we should be able to
call that open session for filter.

In particular, consider the case where a particular AI is related to an event happening, or a particular AI has 
particular knowledge about a prompt contributor or a step in the process. 

Take, for example, review resolution. Say a review has been resolved at some point. Now it's in the prompt contributor
for some other agent. So not any agent will know exactly what happened besides that review agent. And we keep all the
sessions open for the entire time so we can actually call that review agent and ask him to provide input on that 
prompt. And the best part is that it'll be sort of cheap, because the input token cost is cheap, and really all it'll
be doing is outputting a single tool call. Moreover, we use domain specific language models at that point, so they'll
probably be cheaper.

So there is an important invariant here. Sending a chat session to the model that is currently working with some 
other structured response produces a race condition effectively, so all eligible open sessions is.

ALL_OPEN_SESSIONS set difference ALL_RUNNING_SESSIONS_WITH_CURRENT_STRUCTURED_OUTPUT

So we have

# ACTION_SESSION

This one is able to work because consider this - in the session (it may not be open yet) we're going to call, we're not
in the middle of calling it already because we haven't called it yet for this one. It's easy - we just retrieve the last
chat session from our repo that starts with the current request ID.
 
# ALL_AVAILABLE_SESSIONS_W_[RESOLUTION_MODE]

In this one, we send a request to all the available sessions. Then we send another request to some other model with some
context to "resolve" the priority, or to cancel out some of them, etc. It's got a sort of GRPO vibe to it.

# SMART_SESSION_ROUTER

We could use something like https://github.com/ruvnet/ruvector to determine which session to route to - effectively it
takes 

ALL_OPEN_SESSIONS set difference ALL_RUNNING_SESSIONS_WITH_CURRENT_STRUCTURED_OUTPUT

then chooses the best one

