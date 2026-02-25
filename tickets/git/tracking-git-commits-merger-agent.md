Currently there is a MainWorktreeContext, SubmoduleWorktreeContext, and all of that. 

First of all, we're using "branches". So while nice, the problem with them is that they point to something mutable. So we should probably replace anything that is String branch, with some GitRef abstraction, which contains, branch name, commit ref, etc.

Moreover, as we progress through the process, we can do something interesting, and track the git commit history.

This is nice for a couple of reasons.


1. We can keep a history of the exact sources - as long as we don't rewrite the git commit history too much, or if we do it in the context of the framework, then it can say, this was the git commit ref there.   
2. We can track the evolution of the repository through the process a bit better
3. If we see a detached head, for instance, we don't have to worry too much, we can just switch, checkout, etc. We can make sure we're pointing to the right thing - the commit ref.

So because of this idea, for the merge agent, the structured response should contain some information about "rewrites". Such as mapping from one commit ref to another, etc. Because we like this idea (sort of naively), that we should be able to reconstruct a lot of the state from our artifacts.

It's a bit lofty, but we can add to the merge agent, if you're going to rewrite history, please use this...

And then we can say to our regular agents, please don't rewrite history, that's really only for the MergerAgent. 

So this means MergerAgent will have two LLM calls. 

1. Ask the MergerAgent to both return a MergerAgentResult describing the result. Then we need to decorate this as a result decorator, make sure to emit it as an artifact. This should have all request and result decorators before it and after, for all enrichment.
2. MergerRouting - which can then have MergerAgentResult removed from it in the record - no reason for MergerRouting to have a MergerAgentResult field at all anymore because that's happening in the first call.
