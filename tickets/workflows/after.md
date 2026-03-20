After your finished, I'd like you to search through the prompt-health-check, and the other propagators for the advice.

I'd like you to consider each of these, make recommendations for change, which we'll then review together.

Additionally, I'd like you to search through the logs for:

- outstanding issues (maybe we can gather more information for theories about why)
- more evidence 
- to add to outstanding issues

In particular, one of the big ones, and a good example, is orphan artifacts. So we'd check to see what type is producing
an orphan, gather more information and add them.

Another is discovery agents, planning agents trying to execute file operations such as writing. This is an example where
we need to gather some information, to see in which cases this happens.

Or, take, for example, forgetting instructions after compaction event. We would gather when it happened, to see when
we'd want to inject instructions again after compaction.