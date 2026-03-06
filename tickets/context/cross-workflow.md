One of the things that will be difficult to include in a good way is for the agent to search through all of the tools.

The episodic memory exists to provide hooks here, but the mental models get updated a lot, and the postgres database
gets branched for each workflow.

So, the context manager tool should be able to use the episodes as an index to find relevant parts of the graph across
workflows. In particular, the index is an embedding. So this allows for the episodic memory tool to provide a hook into
the episode, then some keywords can be retrieved from there, the key, and then it can be searched by expanding the 
results from there. So then we have an agentic RAG idea starting with the episode, for context manager to pull in some
information from the episode.

However, this doesn't solve the case where there are parallel agents working. It would be best to only branch tables 
and records that need to be merged, and do it lazily. In this case, the episodic memory would be fine because it's 
embedding an episode, and no two episodes would overwrite each other. And then the mental model would be branched, and
then that would only be merged. So this could be done relatively easily by updating the hindsight code, and just 
creating the tables based on the session. And then when we pull hindsight out and add it as Java it's easily completed.

So it's really the highest level that branches the workflow that should do and synchronize the merge. And it's best for
each level to create it's own branch of the database and for then them to merge it. So for instance, we can recursively
do it just like we recursively do the branching and merging. 

Basically every time an agent that can use the memory tool gets created, then a new branch is created, and then where
that branch is created when that ends it gets merged. 