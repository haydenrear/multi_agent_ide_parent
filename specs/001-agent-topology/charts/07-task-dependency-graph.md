# Task Dependency Graph

## Phase Dependencies & Critical Path

```mermaid
flowchart TD
    P1["Phase 1: Setup<br/>T001-T002<br/>2 tasks"]
    P2["Phase 2: Interrupt Simplification<br/>US9 — P0 Pre-requisite<br/>T003-T026<br/>24 tasks"]
    P3["Phase 3: Communication Foundation<br/>US1/US7 shared types<br/>T027-T043<br/>17 tasks"]
    P4["Phase 4: Interrupt AddMessage<br/>US9 completion<br/>T044-T046<br/>3 tasks"]
    P5["Phase 5: list_agents<br/>US1 — P1<br/>T047-T057<br/>11 tasks"]
    P6["Phase 6: call_agent<br/>US2 — P1<br/>T058-T060<br/>3 tasks"]
    P7["Phase 7: Topology Config<br/>US3 — P1<br/>T061-T062<br/>2 tasks"]
    P8["Phase 8: Loop Detection<br/>US4 — P2<br/>T063-T064<br/>2 tasks"]
    P9["Phase 9: Events<br/>US5 — P2<br/>T065-T067<br/>3 tasks"]
    P10["Phase 10: Prompt Contributor<br/>US6 — P2<br/>T068<br/>1 task"]
    P11["Phase 11: Call Controller<br/>US7 — P1<br/>T069-T073<br/>5 tasks"]
    P12["Phase 12: Justification Prompts<br/>US8 — P1<br/>T074-T075<br/>2 tasks"]
    P13["Phase 13: Polling<br/>US7 support<br/>T076-T080<br/>5 tasks"]
    P14["Phase 14: Topology Docs<br/>US10 — P2<br/>T081-T088<br/>8 tasks"]
    P15["Phase 15: Polish<br/>T089-T094<br/>6 tasks"]

    P1 --> P2
    P2 --> P3
    P2 --> P4
    P3 --> P5
    P3 --> P11
    P5 --> P6
    P5 --> P7
    P5 --> P10
    P6 --> P8
    P6 --> P9
    P11 --> P12
    P11 --> P13
    P2 --> P14

    P6 --> P15
    P7 --> P15
    P8 --> P15
    P9 --> P15
    P10 --> P15
    P12 --> P15
    P13 --> P15
    P14 --> P15
    P4 --> P15

    style P1 fill:#666,color:#fff
    style P2 fill:#c33,color:#fff
    style P3 fill:#c33,color:#fff
    style P4 fill:#963,color:#fff
    style P5 fill:#c33,color:#fff
    style P6 fill:#c33,color:#fff
    style P7 fill:#36c,color:#fff
    style P8 fill:#36c,color:#fff
    style P9 fill:#36c,color:#fff
    style P10 fill:#36c,color:#fff
    style P11 fill:#c33,color:#fff
    style P12 fill:#963,color:#fff
    style P13 fill:#963,color:#fff
    style P14 fill:#4a4,color:#fff
    style P15 fill:#666,color:#fff
```

Legend: Red = critical path, Blue = secondary path, Orange = support work, Green = independent, Gray = bookends.

## Critical Path (longest dependency chain)

```mermaid
flowchart LR
    CP1["Setup<br/>2 tasks"] --> CP2["Interrupt<br/>Simplification<br/>24 tasks"] --> CP3["Communication<br/>Foundation<br/>17 tasks"] --> CP5["list_agents<br/>11 tasks"] --> CP6["call_agent<br/>3 tasks"] --> CP8["Loop Detection<br/>2 tasks"] --> CP15["Polish<br/>6 tasks"]

    style CP1 fill:#c33,color:#fff
    style CP2 fill:#c33,color:#fff
    style CP3 fill:#c33,color:#fff
    style CP5 fill:#c33,color:#fff
    style CP6 fill:#c33,color:#fff
    style CP8 fill:#c33,color:#fff
    style CP15 fill:#c33,color:#fff
```

**Critical path length**: 65 tasks (sequential minimum)

## Parallel Execution Lanes

```mermaid
gantt
    title Parallel Execution Lanes
    dateFormat X
    axisFormat %s

    section Lane A (Critical)
    Setup (T001-T002)              :a1, 0, 2
    Interrupt Simplification (T003-T026) :a2, after a1, 24
    Communication Foundation (T027-T043) :a3, after a2, 17
    list_agents (T047-T057)        :a5, after a3, 11
    call_agent (T058-T060)         :a6, after a5, 3
    Loop Detection (T063-T064)     :a8, after a6, 2
    Events (T065-T067)             :a9, after a6, 3
    Polish (T089-T094)             :a15, after a8, 6

    section Lane B (Controller)
    Call Controller (T069-T073)    :b11, after a3, 5
    Justification Prompts (T074-T075) :b12, after b11, 2
    Polling (T076-T080)            :b13, after b11, 5

    section Lane C (Independent)
    Interrupt AddMessage (T044-T046) :c4, after a2, 3
    Topology Config (T061-T062)    :c7, after a5, 2
    Prompt Contributor (T068)      :c10, after a5, 1
    Topology Docs (T081-T088)      :c14, after a2, 8
```
