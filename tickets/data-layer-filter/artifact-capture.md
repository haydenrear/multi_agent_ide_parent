In propagate, we're working on having the bigger LLM save it's filters and mappers, so propagation happens with lower
amount of tokens in a more efficient and pin-pointed way.

So we'll also want to capture how these translate into downstream performance, which means saving them as artifacts 
in the process. 

So this is all happening during movement to controller agent and movement into controller supervisor skill.