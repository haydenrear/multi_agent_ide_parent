See agent-model-prompt-results

So those onboarding tools need to be written and integrated with - mostly it's just a prompt, but the onboarding of the repository to generate the episodes of memory and the context packs is important. 

Note that there is a parent repository and child repositories, and so the ordering of the onboarding of the submodules matters and the args to the tool for rewriting and onboarding of episodic memory will be including this information. 

It's likely that the model can be directed to work on the whole repository, rewriting for the parent and all submodules, because we'll have the list of submodules, however we'll probably need to experiment with onboarding the submodules separately one at a time, adding those episodes and then adding the parent? Or alternately the parent first and then the sub-modules. It's probable that the model will skip commits if it starts with the parent, however the model will probably be smart enough to accept the entire repository at once, if directed to focus on doing a depth first approach, and then cross-referencing with the other repositories.

In any case, the initial step of indexing the entire repository will need to accept arguments for whether it's a submodule, and which git modules exist, and then it chunks and embeds it. Because this tool will exist, and because the timestamps will be included in the chunks along with the code changes, the model can use that search as well to find the similar commits across the multiple submodules.