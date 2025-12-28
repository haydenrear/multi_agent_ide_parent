# Interrupt Routing Matrix

## Overview

Interrupts are processed by sending an interrupt sequence to the active agent using the node ID as the memory ID. The agent result is stored on the originating node before any interrupted status event is emitted. Once the interrupted status event is emitted, the corresponding interrupt node is created to drive event-based routing and continuation.

## Interrupt Types

| Interrupt Type | Trigger Source | Agent Sequence | Node Status Emitted | Interrupt Node Created | Resume Behavior |
|---------------|----------------|----------------|---------------------|------------------------|-----------------|
| HUMAN_REVIEW  | agent result or user request | sent with memory ID = node ID | WAITING_INPUT | ReviewNode (human) | resume origin on approval |
| AGENT_REVIEW  | agent result | sent with memory ID = node ID | WAITING_REVIEW | ReviewNode (agent) | resume origin on approval |
| PAUSE         | user request | sent with memory ID = node ID | WAITING_INPUT | InterruptNode (pause) | resume origin on input |
| STOP          | user request | sent with memory ID = node ID | CANCELED | InterruptNode (stop) | no resume |
| PRUNE         | agent/user request | sent with memory ID = node ID | PRUNED | InterruptNode (prune) | no resume |
| BRANCH        | agent request | sent with memory ID = node ID | WAITING_INPUT | InterruptNode (branch) | resume origin on branch decision |

## Required Ordering

1. Send interrupt sequence to agent with memory ID = node ID.
2. Store agent result on originating node.
3. Emit interrupted status change event for originating node.
4. Create interrupt node based on interrupt type and link to origin.
5. Resolve interrupt by routing back to origin or terminating, based on type.
