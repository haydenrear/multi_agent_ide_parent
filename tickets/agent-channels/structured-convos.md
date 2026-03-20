# Structured Agent-Controller Conversations

## Problem Statement

During a supervised run targeting "parallel execution support," the discovery agent misinterpreted the goal. Instead of understanding that the system needed to **accept a branch parameter from the goal request** and thread it through the workflow, the discovery agent grep'd for hardcoded `"main"` string literals and treated them as bugs. The planning agent dutifully decomposed those findings into tickets. The controller (Claude Code) rubber-stamped propagator outputs that said "in-domain, no escalation" without independently verifying whether the plan addressed the actual goal. The user also wasn't brought into the loop on ambiguities.

Result: ~2 hours of ticket execution producing cosmetic fallback-chain changes to GitWorktreeService.java that were already working correctly, while the actual goal (accepting a branch parameter at the goal level) was never addressed.

## Root Cause Analysis

1. **Discovery interpreted "what" instead of "why."** It found code patterns (`"main"` literals) rather than understanding the architectural gap (no branch parameter flows from goal → worktree creation).
2. **No adversarial semantic review.** Propagators checked form (duplication, ambiguity, in-domain) but not substance (does this plan cover all stated requirements?).
3. **Controller went on autopilot.** Acknowledged phase transitions and propagator outputs without mapping goal requirements to plan outputs. Said "excellent implementation" for 118 lines without asking "does this solve what the user asked for?"
4. **User wasn't consulted on ambiguities.** The controller should have escalated to the user when the discovery interpretation was narrow.

## Proposal: Mandatory Justification Conversations

### Core Idea

Each agent must open a **structured conversation** with the controller (and potentially other agents) to justify its outputs before they're accepted. This replaces the current fire-and-acknowledge pattern with an adversarial review loop.

### Conversation Structure

Agents get a prompt addition that:
1. Requires them to **call the controller as a tool** at key points (start of work, before submitting results)
2. Provides a **justification template** specific to the agent type
3. Defines **what must be covered** in the conversation before the output is accepted
4. Sets a **message budget** (k messages) — if alignment isn't reached within k, escalate to user

### Phase-Specific Conversations

#### Discovery Agent → Controller

**When:** Before submitting discovery findings to the collector.

**Must justify:**
- "Here is my interpretation of what the goal requires" (not just "here's what I found")
- For each finding: "This addresses goal requirement N because..."
- For each goal requirement: "This is covered by finding M" or "I could not find code related to this requirement"

**Controller responsibilities:**
- Independently verify the interpretation against the goal text
- Check: can I map every goal requirement to a finding?
- Check: can I map every finding to a goal requirement?
- **Escalate to user** if there are unmapped requirements or if the interpretation seems narrow
- Push back if findings are pattern-matching (grep for strings) rather than architectural understanding

**Example conversation:**
```
Discovery Agent: "I interpreted the goal as requiring 3 changes: (1) fix hardcoded
'main' fallbacks, (2) add submodule merge step, (3) support concurrent clones.
Finding 1 addresses goal requirement about branch support because..."

Controller: "The goal says 'accept a branch from the goal request.' Where in your
findings is the entry point for that? GoalExecutor line 37 defaults baseBranch —
did you consider that as the starting point?"

Discovery Agent: "You're right, I missed that. The entry point should be..."
```

#### Discovery Orchestrator → Controller

**When:** Before dispatching discovery agents.

**Must justify:**
- Each agent request: what area it covers, why that area is relevant to the goal
- The ordering of agents
- That the set of agents covers the full scope of the goal

**Controller responsibilities:**
- Verify coverage against goal requirements
- **Ask user** if the scope seems too narrow or too broad

#### Planning Agent → Controller

**When:** Before submitting ticket decomposition.

**Must justify:**
- Each ticket with a traceable link to a goal requirement: "TICKET-N addresses goal requirement (M) because..."
- The dependency ordering between tickets
- That no goal requirement is left unaddressed

**Controller responsibilities:**
- Verify every goal requirement has at least one ticket
- Verify every ticket maps to a goal requirement (no scope creep)
- **Ask user** to confirm the ticket decomposition covers their intent

#### Ticket Agent → Controller

**When:** After completing work, before submitting results.

**Must justify:**
- "Here's what I changed and why it addresses the ticket requirements"
- "Here's what I verified (compilation, tests, etc.)"
- "Here's what I did NOT change and why"

**Controller responsibilities:**
- Review actual code changes against ticket acceptance criteria
- Verify changes are in the correct worktree/repository
- Verify no out-of-scope changes

### Controller (Claude Code) Pre-Approval Checklist

Before approving any phase transition, the controller MUST complete:

#### Discovery → Planning Gate
- [ ] Extract concrete requirements from goal text into numbered list
- [ ] Map each requirement to a discovery finding (flag unmapped ones)
- [ ] Map each discovery finding to a requirement (flag unmapped ones)
- [ ] **Ask user**: "Here's my understanding of the requirements and how discovery mapped to them. Does this look right?"
- [ ] Verify discovery looked at entry points (API, config) not just internal code

#### Planning → Tickets Gate
- [ ] Map each ticket to a goal requirement
- [ ] Map each goal requirement to at least one ticket
- [ ] Verify ticket dependencies make sense
- [ ] **Ask user**: "Here's the ticket decomposition. Does this cover what you intended?"
- [ ] Verify tickets target the right files/areas

#### Per-Ticket Completion Gate
- [ ] Read the actual diff (not just stats)
- [ ] Verify changes address the ticket's acceptance criteria
- [ ] Verify changes are semantically correct (not just syntactically valid)
- [ ] Verify correct worktree/repository

#### Ticket Collection → Done Gate
- [ ] Review all merged changes holistically
- [ ] Verify the original goal requirements are met by the combined changes
- [ ] **Ask user**: "Here's what was done. Does this meet your expectations?"

### User Responsibilities

The user is part of the review loop, not a passive observer:

- [ ] **At goal submission**: Provide concrete requirements, not just a description. Or expect the controller to extract and confirm them.
- [ ] **At discovery → planning gate**: Confirm the controller's requirement mapping. Flag if the interpretation is wrong.
- [ ] **At planning → tickets gate**: Confirm the ticket decomposition. Flag missing or unnecessary tickets.
- [ ] **At completion**: Review the final changes against original intent.

The controller should **actively ask the user** at each gate rather than proceeding silently. Better to pause for 30 seconds of user confirmation than waste 2 hours on the wrong plan.

### Controller → User Conversations (During Flow)

The controller doesn't just ask the user at gates — it asks questions **during the conversational flow** whenever something is unclear, surprising, or could go in multiple directions. This is part of the tunable conversational topology.

**When to ask the user:**
- When the controller spots ambiguity in the goal that agents haven't surfaced
- When the controller disagrees with an agent's interpretation but isn't sure of the right answer
- When an agent's justification conversation raises a question the controller can't resolve alone
- When propagator outputs conflict with the controller's own assessment
- When the controller notices the plan is narrower or broader than expected
- When the controller would otherwise have to make an assumption to proceed

**How this fits the topology:**
The controller is a node in the conversation graph, not just a checkpoint. It has conversations with agents (structured, per the templates above) AND with the user (ad-hoc, driven by uncertainty). The user can tune this too — "ask me less about X, ask me more about Y" — and those preferences become part of the conversational structure.

**Anti-pattern to avoid:** The controller should NOT batch up questions for the next gate. If a question arises during a discovery agent conversation, ask the user *now*, not at the discovery→planning gate. The cost of interrupting is low; the cost of proceeding on a wrong assumption compounds through every subsequent phase.

**Example:**
```
[During discovery agent → controller conversation]

Discovery Agent: "I found 4 hardcoded 'main' fallbacks. I believe these are the
root cause of the parallel execution gap."

Controller (to user): "The discovery agent is interpreting the goal as 'fix hardcoded
main references.' But the goal also says 'accept a branch from the goal request' —
should the focus be on adding a branch parameter to the goal API, or on fixing
internal fallbacks, or both?"

User: "The branch parameter. The fallbacks are fine as defaults."

Controller (to discovery agent): "The user clarified: the entry point is accepting
a branch parameter at the goal API level. The internal 'main' fallbacks are
acceptable defaults. Please refocus your findings on the goal→worktree branch
threading path."
```

This three-way conversation (agent ↔ controller ↔ user) is the most valuable topology for catching misinterpretations early. The tuning question is: how often should the controller pull the user in? Start with "whenever uncertain" and dial back based on user feedback about noise level.

## Implementation Approach

### Phase 1: Controller-side checklists
- Add the pre-approval checklists to the controller skill's workflow
- Controller extracts requirements from goal text at the start
- Controller asks user to confirm at each gate

### Phase 2: Agent → Controller tool calls
- Add a "call controller" tool to each agent type's prompt
- Define justification templates per agent type
- Set message budget (k=3 initial, tune based on observation)

### Phase 3: Agent → Agent communication
- Add prompt contributor listing which agents are available to talk to
- Add conversation starters and examples for each pairing
- Discovery agent can ask planning agent for clarification; ticket agent can ask discovery agent about findings

### Phase 4: Tuning
- Observe conversations to identify where prompts need to be more/less decisive
- Adjust k-message limits based on actual conversation lengths
- Update justification templates based on failure modes observed
- Maintain a requirements satisfaction document that tracks what was conversed about for each gate

## Requirements Tracking Document

The controller maintains a living document during each goal execution:

```
Goal: [goal text]

Extracted Requirements:
  R1: [requirement] — Status: [unaddressed | discovered | planned | implemented | verified]
  R2: [requirement] — Status: ...

Discovery Mapping:
  R1 → Finding F3 (justified in discovery conversation msg #2)
  R2 → NOT FOUND — escalated to user at [timestamp]

Planning Mapping:
  R1 → TICKET-001 (justified in planning conversation msg #1)
  R2 → TICKET-003 (justified in planning conversation msg #3)

Completion:
  R1 → TICKET-001 merged, changes verified in conversation msg #1
  R2 → TICKET-003 merged, changes verified in conversation msg #2
```

This makes requirement coverage explicit and auditable at every phase.
