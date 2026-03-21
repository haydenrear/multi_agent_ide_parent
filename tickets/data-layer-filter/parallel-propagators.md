I noticed propagators don't typically look through the code to validate anything.

However, we could do something interesting.

Parallel propagators, sort of like consider 5 parallel agents, all 0.1-0.3 or something, running at the same time, all
read only, no write tools at all, all parsing the repo super fast and validating the result. Then, only it propagates
to review. 

It may be better, however, to give the controller a tail tool or something, kick off parallel propagators, but let the
workflow continue, and if they find something weird after the fact, they can go back or something.

It's sort of like, there is probably some value in a judge framework, powered by agentic tooling, throw 5-10 agents at it
with tools, to validate. Even gpt-oss or mini, saying, look through the repo, identify any inconsistencies or missing
in this.

We can start by just adding 5 propagators pointing to small models, providing worktree context, and then asking the
controller to, once they are all done, take action. 

I think it's a bit cooler to do it trailing, in parallel.

Putting it as an async propagator (adding an async option), and then registering some number of them, and having them
say things like review this, starting several of them, comparing the outputs to see if any of them find anything, or
each of them asking to review a different part, such as infrastructure, adjoining, glue code, etc.

It's a nice way to do review, because we can activate, deactive specifics, and learn, how do different review prompts
help.