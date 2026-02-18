Building loops as an a2-ui component and integrating with the embabel AgentInterface TicketDispatchSubagent.

Goes inside TicketDispatchSubagent but mostly as a prompts/tools. Whenever the tickets are being kicked off, beforehand
the agent goes into an interrupt. The TicketAgentInterrupt however is special because the user receives an a2-ui form
that allows them to build out the desired loops and prompts.

There can be multiple loops set up for the agent. Outer loops are more expensive, such as debugging loops or integration
testing loops, web testing loops, etc., inner loops are cheaper and help narrow down and specify the issues, such as 
unit testing loops. Being able to track information about the loops allows for cost and benefit calculations for the 
agent, based on previous data. So the LLM will analyze history of loop, and create next version of the loop or make, 
suggesting about the versioning of the loop, like a  developer advances their testing strategies and development 
workflow. The chat can then happen about versioning the loop containing data about loops and episodic memories about 
that loop. However starting it's just

1. being able to create new loop, new loop template
2. being able to version the loop, make adjustments,
3. tracking the loop and the execution of the ticket according to that workflow

So the most important thing about this is that the loop information is able to be tracked and traced so that the 
developer can refine their workflow over time.

So to make sure that we're able to trace the loops, the thread local variable for the ACP session needs to be extended
to include metadata that we add to the graph events. It's basically just simple trace ID information, and we're going
to be adding that, a versioned loop ID identifier, with phase of loop as well.

Additionally, to start the agent will recursively be repeated. It will return once it thinks we need to break the
loop, or go to outer loop, or inner loop. It will be included in the prompt the information about the loops, and then
this allows the events to be emitted and the trace metadata loop versions to be updated. 


---

This can probably be merged in with the inclusion of skills dynamically for each agent, for ticket agent. In particular,
loop can be considered a sort of dynamic skill, whereby the skill.md has instructions for adding to a directory for a 
particular project, for a particular loop. Then, when including information from that skill in the tree view, you'll
include that loop. And then that directory, for that project and that loop, is getting versioned. The nice thing is 
that for the skill, we're versioning the prompt template. So it gets versioned implicitly in the database. However,
this does mean that for skills we'll have to add an additional child for references, and version each of these 
independently, and then the loop will be one of the references. And the agent can then make changes to that directory,
and the collector can then merge these changes in - remember that there's a skills directory per project as well, so 
these changes get merged into that, and then pushed up. 