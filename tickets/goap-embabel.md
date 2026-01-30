# Goal Oriented Agent Planning (GOAP)

Currently we have so many agents as invocations - but this will be changed soon so that the return values
of the agents cause routing to occur within the sub-graphs. And then we'll use the parameters implicitly to
determine how the routing works and the dependencies. We'll still use the computation graph, but the sub-graphs
will be created by embabel - one for each @Agent - and then in each agent there are so many actions. They will then
route to each other based on the return types.

Here's an example in Kotlin - we'll be using Java.

```kotlin
// Must implement the SomeOf interface
data class FrogOrDog(
    val frog: Frog? = null,
    val dog: Dog? = null,
) : SomeOf

@Agent(description = "Illustrates use of the SomeOf interface")
class ReturnsFrogOrDog {
    @Action
    fun frogOrDog(): FrogOrDog {
        return FrogOrDog(frog = Frog("Kermit"))
    }

    // This action will only run if frog field was set
    @AchievesGoal(description = "Create a prince from a frog")
    @Action
    fun toPerson(frog: Frog): PersonWithReverseTool {
        return PersonWithReverseTool(frog.name)
    }

    @AchievesGoal(description = "Walk a dog")
    @Action
    fun toDog(dog: Dog): WalkDog {
        return WalkWog(dog);
    }
}
```

So we'll be doing something similar. Managing the routing across agents within a sub-graph - which are the orchestrator, agent, and collector, as well using subAgents to manage the routing between sub-graphs, between planning and discovery, or going back from discovery to planning, or going from either of those to ticket, or going from any of those to review, or going from review back to the previous node. For each of these @Agent, we'll also simplify things by adding an interrupt action, which will do things like doing review, human review, waiting for human response. Then, in the AgentEventListener, we'll only be managing the routing between agents, and remove the routing that currently exists between the actions that we're adding.

In other words, the orchestrator, agent, and collector are currently each individual @Agent, but we'll be making each one of those into multiple actions in one agent, and using the someOf to route between them. Then, for each one of the options for each of the agents, we have an action. For example, the collector has the option to route back to orchestrator, so one of the options in it's SomeOf is OrchestratorRouterResult, and this applies to each agent. So then, the collector can also emit an event to the orchestrator graph on finishing, which will get picked up and the next @Agent will get called in the event listener.

The key here to make sure we're integrating everything with the UI is that we'll add event listeners and emit the events, which will be simple because we can inject the event bus into the agents and call it in the functions based on the routing key - then those events get translated to the ui events and pushed with the SSE event emitter automatically. 

So currently we're putting interrupt type in every agent's response. Instead, what we'll do, is make a data class SomeOf, and make one of the items InterruptType. Additionally, where we currently have CollectorDecisionType, for each return type we'll want to add fields in the SomeOf for each of the options. Then it will automatically route to the given agent. So this is the sug-graph - we'll.


---

Ok so when you add a record object to the blackboard that has an object that's already hidden,
then the new object will be hidden as well?

Should it not be hidden, should it be un-hidden?

Should it be based on more of an object equality idea?

---

Update PromptProvider for each type of context, update to always use prompt provider from blackboard
history.