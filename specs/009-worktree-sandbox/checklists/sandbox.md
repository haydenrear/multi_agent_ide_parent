# Checklist: Worktree Sandbox Requirements

## Requirement Completeness
- CHK001 Are all agent request types that implement AgentRequest explicitly listed as needing worktree context? [Completeness, Spec §FR-004]
- CHK002 Are requirements defined for how RequestContext is created, updated, and stored across request lifecycles? [Completeness, Spec §FR-004a–FR-004c]
- CHK003 Are sandbox boundaries defined for both main worktree and submodule worktrees? [Completeness, Spec §FR-018]
- CHK004 Are the required error response fields for sandbox violations explicitly specified? [Completeness, Spec §FR-014]
- CHK005 Is the ticket orchestrator’s responsibility for per-ticket worktree creation fully specified (when, where, and naming)? [Completeness, Spec §FR-005–FR-007]

## Requirement Clarity
- CHK006 Is “worktree path information” defined with precise fields (IDs vs paths vs both)? [Clarity, Spec §User Story 1]
- CHK007 Is “blocked with an appropriate error message” defined with exact error content or schema? [Clarity, Spec §User Story 2/3/4, Spec §FR-014]
- CHK008 Is “same directory as the original code” defined concretely (sibling path rules, naming format)? [Clarity, Spec §FR-006]
- CHK009 Is the session ID source explicitly tied to ArtifactKey.value()/contextId without ambiguity? [Clarity, Spec §FR-004c, Spec §FR-015]

## Requirement Consistency
- CHK010 Do header-based resolution requirements align with decorator-based propagation without conflicting sources of truth? [Consistency, Spec §FR-001–FR-003, Spec §FR-015–FR-017]
- CHK011 Are read/write/edit sandbox rules described consistently (same allow/deny semantics and path normalization approach)? [Consistency, Spec §FR-010–FR-013]

## Acceptance Criteria Quality
- CHK012 Do the acceptance scenarios specify measurable outcomes for “blocked” operations (response payload shape, status, or error code)? [Acceptance Criteria, Spec §User Story 2/3/4]
- CHK013 Are the acceptance criteria for submodule access explicit enough to validate allowed vs denied paths? [Acceptance Criteria, Spec §User Story 6]
- CHK014 Are the header-resolution acceptance criteria specific about expected failure behavior for invalid headers? [Acceptance Criteria, Spec §User Story 5]

## Scenario Coverage
- CHK015 Are alternate flows defined for missing worktree context at request creation time? [Coverage, Gap, Spec §Edge Cases]
- CHK016 Are recovery/cleanup behaviors specified when a worktree is deleted during agent operations? [Coverage, Gap, Spec §Edge Cases]
- CHK017 Are scenarios defined for shared worktrees across agents (allowed or disallowed)? [Coverage, Gap, Spec §Edge Cases]

## Edge Case Coverage
- CHK018 Are symbolic link behaviors specified when links point outside the worktree? [Edge Case, Spec §Edge Cases]
- CHK019 Is path traversal handling explicitly defined (normalization rules and rejected patterns)? [Edge Case, Spec §FR-013]
- CHK020 Are requirements defined for operations on the worktree root directory itself? [Edge Case, Spec §Edge Cases]

## Non-Functional Requirements
- CHK021 Is the performance overhead target (<5ms) tied to measurable conditions and scope? [Non-Functional, Spec §Performance Goals]
- CHK022 Are security requirements explicit about how sandbox enforcement failures are logged or audited? [Non-Functional, Gap]

## Dependencies & Assumptions
- CHK023 Are dependencies on GraphRepository/WorktreeRepository behavior clearly stated (what data must exist, when)? [Dependency, Spec §Integration Points]
- CHK024 Are assumptions about MCP header availability documented for all file operations? [Assumption, Spec §FR-015–FR-017]

## Ambiguities & Conflicts
- CHK025 Is there any ambiguity between “worktree ID” and “worktree path” usage in requirements? [Ambiguity, Spec §Key Entities]
- CHK026 Are provider-specific sandbox translation requirements sufficiently specific to prevent divergent interpretations? [Ambiguity, Spec §FR-004f–FR-004i]
