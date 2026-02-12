Happens in stages

# Repo Prep

Git submodule - git filter-repo

# Embedding Updates

For now, we're going to be doing embeddings on git diffs using ast-diff. We step through the repo, produce the diff, embed it using ast-diff, and save it.

# Rewrite History into K 

Now that we have the embeddings, we do a dimensional reduction technique, and do some form of contiguous k-means or other clustering, or PCA with hdbscan, or cosine to decide the divide 

# Agentic Episodes

Then we, for each of these k, run an agent and ask it to work with the memory tool to produce and update insightful mental models. And each agent for it's own k, carrying important stuff to the next through a small message, and then all having access to the memory tool and all the mental models previously created and updated.

---

So those onboarding tools need to be written and integrated with - mostly it's just a prompt, but the onboarding of the repository to generate the episodes of memory and the context packs is important. 

So - the way this will be done the best is to take the git project with it's submodules, and do a git filter repo style merging of the repos into one repo, then performing the rewrite of that commit history. So then, once we have that, we embed that repo history, then do  the rewrite of that repository, and then do episodic memory of that repo - and then delete it.

In particular, after we merge together using git filter-repo ...

Then we'll have a question of how to prompt the thing. 

One way we might try - we can experiment a few with different models, and capture the result, is to

1. do a git reset --soft with some number of commits - perhaps dividing num commits total / num memories max
2. then ask it to "divide the commit into some maximum number of commits", or something like this
3. then repeat this, however with each next (because we divided entire repo into k chunks, each one will produce some t commits, and therefore some t episodes) there will then be the episodes previous to reflect on. And remember that this rewriting of the git history isn't important. What's important is producing some "episodes", and then we just need it to prod for mental models.

Basically it's just doing a git reset --soft some k chunks, then asking it to produce t commits per chunk, where max episodes produced is k * t. And with each next t commit, next t episode, all previous episodes can be reviewed and mental models updated accordingly - remember all we're looking for is a set of mental models encoding an episodic memory, updated through the parsing of the history.

So this is serial. Because we have to have previous episodes for new episodes - and make sure to prod it to update previous - we don't want any garbage from prev deprecated...
