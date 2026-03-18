On the retry we should be able to enable particular filters and transformers with particular prompts, so that if we
get a "prompt too long" on the first try, we have the session cleared already, then it sees the exception, then it runs
the filters and transformers - so we have an attachable on promp_compactor/agent_name/1, or prompt_compactor/any/2.

where compaction needed is the name, then the agent name is the name for which compaction would happen, then number 
signifies the number of retry. So then it can attach to particular prompt contributors by name or regex, or text.

Then the filter/transformer is filtering only particular prompts after particular retries.

Additionally, we'll set a compaction limit, detect the size of the prompt, then run back through them and do it, without
having to send it.

And then we'll do it in order. We'll have a max_prompt_length, it will continue to do compaction transformers, 
compaction filters until we're underneath that max_prompt_length.

And because the compaction can have instructions based on domain knowledge, we can summarize first the things that are 
least likely to carry the most important information per agent.  

So additionally, before we do this, we may want the controller to receive a propagation request, so that it can register
other compaction transformers or filters, specific to that prompt, activate them, then deactivate them afterwards.

---

So we'll just have a prompt_compactor loop that runs these, and it's a bit better because

1. it's domain specific but still emergent, asking to first summarize the least important information 
2. a bigger model can be notified of compaction and compaction can then be escalated


---

So this probably won't be needed really ever. The prompt is never expected to get this big. 