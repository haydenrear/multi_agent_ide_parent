# Task Distribution by User Story

## Tasks per Story

```mermaid
pie title Task Distribution (94 total)
    "US9 Interrupt Simplification (P0)" : 27
    "US1 list_agents (P1)" : 18
    "US7 Call Controller (P1)" : 9
    "US2 call_agent (P1)" : 3
    "US8 Justification (P1)" : 2
    "US3 Topology Config (P1)" : 2
    "US10 Topology Docs (P2)" : 8
    "US5 Events (P2)" : 3
    "US4 Loop Detection (P2)" : 2
    "US6 Prompt Contributor (P2)" : 1
    "Setup/Foundation/Polish" : 19
```

## Story-to-File Mapping

```mermaid
flowchart LR
    subgraph "US9: Interrupt Simplification (27 tasks)"
        direction TB
        S9_1["AgentModels.java<br/>T003-T004, T010-T012"]
        S9_2["AgentInterfaces.java<br/>T007-T009"]
        S9_3["FilterPropertiesDecorator.java<br/>T016-T018"]
        S9_4["InterruptController.java<br/>T019-T021, T045"]
        S9_5["DELETE: ReviewRoute, MergerRoute<br/>T005-T006"]
        S9_6["DELETE: InterruptPromptContributor,<br/>OrchestratorRouteBack<br/>T013-T014"]
        S9_7["InterruptLoopBreaker<br/>T015"]
        S9_8["Schema generator + decorators<br/>T022, T044"]
    end

    subgraph "US1: list_agents (18 tasks)"
        direction TB
        S1_1["AgentModels.java<br/>T027 (AgentToAgentRequest)"]
        S1_2["GraphNode.java<br/>T032"]
        S1_3["PromptContext.java<br/>T036"]
        S1_4["Decorator audit<br/>T037-T042"]
        S1_5["CommunicationTopologyConfig<br/>T047"]
        S1_6["application.yml<br/>T048"]
        S1_7["SessionKeyResolutionService<br/>T049-T054"]
        S1_8["AgentCommunicationService<br/>T055-T056"]
        S1_9["AcpTooling.java<br/>T057"]
    end

    subgraph "US7: Call Controller (9 tasks)"
        direction TB
        S7_1["AgentModels.java<br/>T028-T031"]
        S7_2["GraphNode.java<br/>T033-T034"]
        S7_3["AcpTooling.java<br/>T069-T071"]
        S7_4["AgentConversationController<br/>T072-T073"]
    end

    subgraph "US2: call_agent (3 tasks)"
        direction TB
        S2_1["AgentCommunicationService<br/>T058"]
        S2_2["AcpTooling.java<br/>T059"]
        S2_3["Error handling<br/>T060"]
    end

    style S9_5 fill:#f66,color:#fff
    style S9_6 fill:#f66,color:#fff
```

## File Touch Frequency

Shows which files are modified across multiple phases — high-touch files need careful sequencing.

```mermaid
flowchart TD
    subgraph "High Touch (4+ phases)"
        HT1["AgentModels.java<br/>Phase 2 (removal) + Phase 3 (new types)<br/>T003-T004, T010-T012, T027-T031"]
        HT2["AcpTooling.java<br/>Phase 5 + 6 + 11<br/>T057, T059, T069"]
        HT3["FilterPropertiesDecorator.java<br/>Phase 2 (removal) + Phase 3 (audit)<br/>T016-T018, T037"]
    end

    subgraph "Medium Touch (2-3 phases)"
        MT1["AgentInterfaces.java<br/>Phase 2<br/>T007-T009"]
        MT2["InterruptController.java<br/>Phase 2 + 4<br/>T019-T021, T045"]
        MT3["PromptContext.java<br/>Phase 3<br/>T036"]
        MT4["InterruptLoopBreaker<br/>Phase 2 + 3<br/>T015, T038"]
        MT5["GraphNode.java<br/>Phase 3<br/>T032-T034"]
        MT6["Events.java<br/>Phase 3 + 9<br/>T035, T065"]
        MT7["AgentCommunicationService<br/>Phase 5 + 6<br/>T055-T056, T058"]
    end

    subgraph "Single Touch"
        ST1["SessionKeyResolutionService (NEW)<br/>Phase 5<br/>T049-T052"]
        ST2["AgentConversationController (NEW)<br/>Phase 11<br/>T072-T073"]
        ST3["ActivityCheckController (NEW)<br/>Phase 13<br/>T076"]
        ST4["AgentTopologyPromptContributor (NEW)<br/>Phase 10<br/>T068"]
        ST5["JustificationPromptContributor (NEW)<br/>Phase 12<br/>T074"]
        ST6["CallChainTracker (NEW)<br/>Phase 8<br/>T063"]
    end

    style HT1 fill:#c33,color:#fff
    style HT2 fill:#c33,color:#fff
    style HT3 fill:#c33,color:#fff
    style MT1 fill:#963,color:#fff
    style MT2 fill:#963,color:#fff
    style MT3 fill:#963,color:#fff
    style MT4 fill:#963,color:#fff
    style MT5 fill:#963,color:#fff
    style MT6 fill:#963,color:#fff
    style MT7 fill:#963,color:#fff
    style ST1 fill:#4a4,color:#fff
    style ST2 fill:#4a4,color:#fff
    style ST3 fill:#4a4,color:#fff
    style ST4 fill:#4a4,color:#fff
    style ST5 fill:#4a4,color:#fff
    style ST6 fill:#4a4,color:#fff
```
