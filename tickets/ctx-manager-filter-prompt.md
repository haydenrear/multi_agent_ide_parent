# ContextManagerResultRouting Filtering

ContextManagerResultRouting should have filtered fields based on history. 

If it hasn't been there before, filter it.


---

# ActionContextPack State Machine for Prompts (Exhaustive context state machine w/ sealed interface)

There should also be two context manager prompt contributor factories for when current request is context manager request

1. when an agent routes to it
2. when a degenerate loop is detected

This can be detected based on reason, but reason should really be added to something better, an ActionContextPack - 
which isn't just the action, but also contains cases for particular states in the blackboard history - and then we're
provided with a state machine for the prompt contributors.

We can also create an ActionContextPack to put in PrompContext - and it contains all of this information, the agent
name, action name, all those as enums. Then we can do matching over those ActionContextPack by adding methods, or 
make ActionContextPack itself an interface, an algebra, with mix-ins to match over for prompts ?

I think the ActionContextPack as an interface is best - because what we can do is create it once, based on blackboard
history. Include everything from number loops, last degenerate loop exception if any, etc. Then we can, for each prompt
contributor factory, match over this to determine whether or not to include that prompt contributor. This makes it more
discrete - we have every single case as an algebra.