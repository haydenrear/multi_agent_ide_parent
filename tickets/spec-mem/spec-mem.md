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

- If I run two tickets at the same time, the mental models will need to be merged afterwards. So then the 
  collector needs to be prompted to search through the tool calls for updating the mental models, and perform a merge. 
  The easiest way to do it will be to have a merge operation that the collectors perform, and copying database over
  for writes. In this case, whenever there is an agent that needs to write to the memory bank, the database is copied,
  it's a copy on write operation, and this means a new server is deployed. Then, the collector calls the python file,
  and the tool iterates over the servers that are up, providing the tool calls that happened, and the collector updates
  the mental model - this happens when we want to do concurrent. For now, we just write the python tools, skipping 
  that part of the collector, but the collector still reviews the changes to the mental model, across the dispatched agents,
  and performs the merge. So that means when we split out into .py files, these .py files log the calls, and then collector 
  calls, and they are returned so the collector can then do standard operations on that same database to merge them 
- After every model execution, as a result decorator, or routing decorator, add an LLM call specifically for updating
  mental models for ticket agents and relevant collectors - otherwise it probably won't do it (except discovery, which centered 
  around the memory banks and mental models) -- discovery agents have it already, planning won't really need it, will have
  references to what is in the mental models from discovery - but discovery collector, ticket agents, and ticket collector
  will definitely need to have this step.
- In the discovery orchestrator, as a prompt, prompt it to search for the mental models applicable (later on splitting 
  mental models for parallel), and, for each 
  discovery agent, provide mental model refs to be used as a starting point for recall/mem bank/mental models and discovery (these will)
  be sort of the beginning for the code maps - discovery agents increasingly will use the mental models as starting 
  points for the code maps and discovery results - they will then do search through the code, validating mental models, and, 
  updating them if they are out of date, or updating to add stuff that is missing, and then include summaries of the 
  updates that they made (memory references), along with their code map and discovery results. The memory references are
  then validated by discovery collector using the above mechanism. (to start, these are all non-parallel)
   