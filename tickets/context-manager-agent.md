Got it — you’re right. The **core of this ticket is the Context Manager’s ability to *create context* via tools over BlackboardHistory**, and that must remain first-class alongside StuckHandler recovery and routing semantics.

Below is the **final revised ticket**, preserving **all prior requirements** and **explicitly restoring and elevating the BlackboardHistory tools** as the primary mechanism by which the Context Manager does its job. This is still **high-level / architectural**, with no code or model bindings.

---

# Ticket: Context Manager Agent with Blackboard History Tooling and StuckHandler-Based Recovery

## Status

Proposed

## Problem Statement

In a multi-agent workflow, agents operate with scoped, action-local context, while the full execution history spans:

* multiple agents,
* multiple actions,
* multiple ACP sessions,
* and long-running workflows.

That accumulated state lives in **BlackboardHistory** and the event stream.

Certain situations require **intentional reconstruction of context**, not incremental continuation:

* an agent explicitly determines it lacks sufficient context,
* Review or Merge stages filter information too aggressively,
* the workflow becomes stuck, hung, or enters a degenerate loop.

To address this, the system requires a **Context Manager Agent** whose *primary responsibility* is to **create, curate, and rebuild context** by operating over the full BlackboardHistory using dedicated tools.

---

## Scope

### In Scope

1. Introduce a **Context Manager Agent** whose primary function is **context creation via BlackboardHistory tools**.
2. Define a **tooling surface over BlackboardHistory** for context reconstruction.
3. Integrate **Embabel’s `StuckHandler`** to route stuck or degenerate workflows through the Context Manager.
4. Extend **BlackboardHistory** to support **post-hoc notes/annotations**.
5. Define **deterministic routing semantics** for entering and exiting the Context Manager.

### Out of Scope

* Concrete code, model, or class definitions.
* UI or interactive visualization.
* Embedding/vector search implementations (interfaces may exist, implementation deferred).
* Changes to provider-level context compaction.

---

## Context Manager Agent: Core Responsibility

The Context Manager Agent exists to **create working context on demand**.

It does so by:

* inspecting BlackboardHistory across agents, actions, and sessions,
* selecting, summarizing, pruning, or linking relevant information,
* assembling a new, intentional context for the next step of execution,
* recording reasoning artifacts back into the blackboard.

Context creation is **tool-driven**, not implicit.

---

## BlackboardHistory Tooling (Primary Capability)

### Required Context-Creation Tools

The Context Manager Agent MUST have tools that allow it to actively construct context from BlackboardHistory.

At minimum, the following conceptual tools are required:

1. **History Trace Tool**
   Retrieve the ordered history of events for a specific action or agent execution.

    * Used to understand *what actually happened* during a step.

2. **History Listing / Paging Tool**
   Traverse BlackboardHistory incrementally.

    * Enables scanning backward or forward through long workflows.
    * Supports time windows, agent/action filters, and pagination.

3. **History Search Tool**
   Search across BlackboardHistory contents.

    * Used to locate relevant prior decisions, errors, or artifacts.
    * Text-based search is sufficient at this stage.

4. **History Item Retrieval Tool**
   Fetch a specific history entry by identifier.

    * Used when context creation references earlier events explicitly.

5. **Context Snapshot Creation Tool**
   Persist a *curated context bundle* derived from multiple history entries.

    * Represents the newly constructed working context.
    * Links back to source history items.

6. **Blackboard Note / Annotation Tool**
   Attach notes to BlackboardHistory entries.

    * Used to explain inclusion, exclusion, minimization, or routing rationale.

These tools are **model-agnostic** and **presentation-agnostic**.
They exist solely to enable deliberate context reconstruction.

---

## BlackboardHistory Notes / Annotations (Required)

BlackboardHistory must support **post-hoc annotations**.

### Purpose of Notes

Notes serve as:

* reasoning artifacts,
* recovery diagnostics,
* lightweight indices over history,
* memory aids for future context reconstruction.

### Characteristics

* Notes are additive and non-destructive.
* Notes may reference one or more history entries.
* Notes may include:

    * classification (e.g., diagnostic, routing rationale, exclusion),
    * free-form text,
    * links to related history items,
    * optional tags.

Notes are intentionally **not tied to any specific agent or model**.

---

## Routing Semantics (Authoritative)

### When the Context Manager Agent Is Invoked

The Context Manager Agent is routed to **only** when:

1. **Explicit agent request**
   An agent returns a request targeting the Context Manager because it needs context creation.

2. **Stuck or degenerate execution via `StuckHandler`**
   Embabel invokes the workflow’s `StuckHandler`, which escalates recovery to the Context Manager.

3. **Optional Review/Merge escalation**
   Review or Merge agents explicitly request context reconstruction.

There is no implicit or automatic routing.

---

## StuckHandler Integration

### Role of the StuckHandler

The workflow’s primary agent acts as an **Embabel `StuckHandler`**.

The StuckHandler is responsible for:

* detecting hung, stalled, or degenerate execution,
* recording recovery-relevant information into BlackboardHistory,
* escalating recovery to the Context Manager Agent.

### Separation of Concerns

* **StuckHandler**: detects and escalates.
* **Context Manager Agent**: diagnoses and creates new context.
* **Workflow continuation**: resumes via normal Embabel routing.

The StuckHandler itself does **not** attempt context reconstruction.

---

## Degenerate Loop and Hung State Recovery

### Detection

* Degenerate loop detection is updated incrementally as BlackboardHistory grows.
* Interrupts reset loop detection state.
* Loop or hung conditions surface via Embabel’s stuck detection mechanisms.

### Recovery Flow

1. Execution stalls or loops.
2. Embabel invokes the StuckHandler.
3. The handler escalates to the Context Manager Agent.
4. The Context Manager:

    * traverses history using tools,
    * annotates findings,
    * creates a new context snapshot.
5. The Context Manager returns a **single continuation request**.

---

## Context Manager Return Semantics

* The Context Manager returns a **`SomeOf`-style outcome**, consistent with existing usage.
* **Exactly one branch is populated**.
* That branch is a **request for another agent**, which resumes the workflow.

This enforces simple, deterministic reasoning.

---

## Acceptance Criteria

### AC-1: Context Creation Is Tool-Driven

* Context Manager reconstructs context exclusively via BlackboardHistory tools.
* No reliance on implicit in-memory context.

### AC-2: Notes Are Persisted

* Context reconstruction decisions leave notes in BlackboardHistory.
* Notes are queryable alongside original history.

### AC-3: StuckHandler Escalation

* Hung or degenerate execution routes through the StuckHandler to the Context Manager.

### AC-4: Deterministic Continuation

* Every Context Manager invocation returns exactly one continuation request.

---

## Non-Goals (Reiterated)

* No new orchestration layer.
* No UI.
* No model-specific logic.
* No automatic routing heuristics.

---

## Summary

This ticket establishes the Context Manager Agent as a **context-creation engine**, not merely a recovery hook.

Its defining features are:

* **first-class tools over BlackboardHistory**,
* **annotation and snapshot support**,
* **StuckHandler-based escalation**,
* **deterministic Embabel routing**.

Together, these enable long-running workflows to intentionally reset, rebuild, and reason about context without introducing global control logic or opaque recovery behavior.

---

If you want next iterations, strong follow-ups would be:

* a dedicated ticket on **BlackboardHistory query/index semantics**, or
* a ticket defining **what constitutes “context” vs “history” vs “memory”** at the platform level.

So for this idea, we'll make WorkflowAgent a StuckHandler:

```kotlin
@Agent(description = "Self-recovering agent")  
class RecoveryAgent : StuckHandler {  
      
    @Action  
    @AchievesGoal(description = "Main processing goal")  
    fun processData(data: Input): Output {  
        // Main processing logic that might get stuck  
        return processComplexData(data)  
    }  
      
    override fun handleStuck(agentProcess: AgentProcess): StuckHandlerResult {  
        // Add recovery data to blackboard  
        agentProcess.addObject(RecoveryContext("stuck_at_${Instant.now()}"))  
          
        return StuckHandlerResult(  
            message = "Initiating recovery protocol",  
            handler = this,  
            code = StuckHandlingResultCode.REPLAN,  
            agentProcess = agentProcess  
        )  
    }  
      
    @Action  
    fun recoverWithSpecialAgent(context: RecoveryContext): Output {  
        // Run special agent for recovery  
        return RunSubagent.fromAnnotatedInstance(  
            SpecialRecoveryAgent(),  
            Output::class.java  
        )  
    }

    @Action
    fun handleHungException(exception: HungException): RecoveryResult {
        return RecoveryResult(
            status = "handled_hung",
            action = "break_hung_state"
        )
    }
}
```

So what we'll do is throw a HungException when we detect a degenerate looping behavior. Every time we add an entry to
the BlackboardHistory we will also update the state of our DegenerateLoopDetector. And then if we detect a degenerate loop
we'll throw the DegenerateLoopException with some information about what type of degenerate loop - this will route to our
context manage agent that has the tols in @context-manager-agent.md which can help figure out what's wrong and has the
ability to rewrite the context, including any relevant information from the messages into the previous context and route
to the correct agent.

