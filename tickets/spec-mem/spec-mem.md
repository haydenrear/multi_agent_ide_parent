There are two things, that perform a similar mechanism, which is effectively create/manage mental models of the code.

I noticed that specs are difficult to keep in sync in a standard way, there's a bit of a cognitive burden having so 
many directories. And there isn't much structure across them. Which is why I've considered them not that great a 
mechanism, and considered that although tickets/specs can be helpful, the spec should really be more of a mental model
that is emergently updated.

So when considering how to add the memory/mental model pieces in the most effective way, constantly considering 
evolution of something spec-like, which are really just mental models of the code.

Really the question is how to prompt the LLM to introduce this in the way to capture the most amount of value from each
run, and keep mental models stable.

# Invariants / Requirements
- for merge/collect behavior see spec-agents.md.
- After every model execution, as a result decorator, or routing decorator, add an LLM call specifically for updating
  mental models. See spec-agents.md for merge-collecto ideas.
- In the discovery orchestrator, as a prompt, prompt it to search for the mental models applicable (later on splitting 
  mental models for parallel), and, for each  discovery agent, provide mental model refs to be used as a starting point 
  for recall/mem bank/mental models and discovery (these will)
  be sort of the beginning for the code maps - discovery agents increasingly will use the mental models as starting 
  points for the code maps and discovery results - they will then do search through the code, validating mental models, and, 
  updating them if they are out of date, or updating to add stuff that is missing, and then include summaries of the 
  updates that they made (memory references), along with their code map and discovery results. The memory references are
  then merged by discovery collector using the above mechanism. (to start, these are all non-parallel) - see spec-agents.md
   