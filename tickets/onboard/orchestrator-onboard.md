# Skill

We can most easily produce this as a skill provided by the orchestrator agent.

We do something really weird to start (and then decide whether we want to do it better later).

Provide a skill with shell scripts:

1. repo_prep -> this first does truncation of repo (deleting commits that aren't important anymore) recursively (for all git submodules), then it merges together the submodules into a mono-repo
2. commit_partitioner -> partitions commits into k episodes
3. repo_diff_calculator -> calculates diff over when we last did episodic memory to now

## Stages

1. 


So the key point here is that we just ask the orchestrator something like can you please check to see if the current repo
has been encoded as memory already? Here are your scripts... And then the responses for the scripts guide it. And we say
things like 

1. check to see if anything hasn't been encoded -> and see if the thing being encoded can just be encoded just now (instead of doing any partition at all)
2. if it hasn't been encoded, then prep repo and partition commits
3. once we partition commits, then encode those partitioned commits into the episodic memory

And we don't worry so much.

# Orchestrator Onboarding


One other note is a "diff" over previous.

So when we do the onboarding, and we save the memories and do the tickets, we'll need to save the last onboard pointer. 
And then we compare the last onboard pointer to the last update, and we'll get a diff. And we'll ask the AI to, starting
with the diff, split it into different commits. After which, we'll do an onboarding step starting from there. 

Because we need to keep it in sync, make sure that every episode is included in the memory, and sometimes the user will
perform some actions that need to get encoded in the memory outside of the process.

So then in the orchestrator collector, or the commit, when we update the memory (see mem), we include that diff that 
got encoded into what has been onboarded.

We'll probably want a time stamp of last record, along with a commit ref - and we'll just choose merge over rebase so 
as to keep the commit hashes stable.