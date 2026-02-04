See agent-model-prompt-results

So those onboarding tools need to be written and integrated with - mostly it's just a prompt, but the onboarding of the repository to generate the episodes of memory and the context packs is important. 

So - the way this will be done the best is to take the git project with it's submodules, and do a git filter repo style merging of the repos into one repo, then performing the rewrite of that commit history. So then, once we have that, we embed that repo history, then do  the rewrite of that repository, and then do episodic memory of that repo - and then delete it.