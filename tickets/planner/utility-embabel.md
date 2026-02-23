Replace the blackboard history planner with the utility planner or a planner that calls a function on the agent
to ask which one to do next. So in this case, there would be a FUNCTION planner, which passes in the blackboard
and does what the blackboard history planner does. So in this case, the @Agent or @EmbabelComponent has a function
or the Action has a reference to a method that gets called after to decide which plan step to take.

However, I'm more interested in using the blackboard more, especially when adding the controller. And the thing we add
after the controller, the controller supervisor.

It's a bit in the realm of considering a large model operating as a supervisor planner with the context manager tooling,
or with tooling that operates over the blackboard, blackboard history, things streamed into the blackboard. And then 
the blackboard ultimately being saved as artifacts, which are then queryable across conversations, however that would
be. And then additionally, we want the ability for even the agents themselves to create/update more durable abstractions
that evolve naturally, like reflect works. 

And providing interfaces over the items in the blackboard to enable tooling over it.

For example, consider some interfaces:

GraphNode
ListNode
Pageable
Traversable

...

or just

Indexed<..> or Indexed<Composite>

where Composite is a composite of & & & .... methods that are traversed in different ways.

etc. 

And if the thing in the blackboard implements it, it enables the tool to be able to traverse the graph in a particular
way. 

So in other words, I'd like to sort of make the tool objects indexed in various ways of expanding them. 

So then the context manager becomes more of a supervisor, and gets routed to at each step, enriching according, watching
and then we can drop some of the extra context from the prompts.