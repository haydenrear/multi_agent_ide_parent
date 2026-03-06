Several graph nodes don't exist when they should.

i.e.

```java
            case AgentModels.CommitAgentRequest ignored -> {
                // Internal commit action: do not create a dedicated workflow node.
            }
            case AgentModels.MergeConflictRequest ignored -> {
                // Internal merge-conflict action: do not create a dedicated workflow node.
            }
            case AgentModels.AiFilterRequest aiFilterRequest -> {
//                skipping this - no need.
            }
```

in StartWorkflowRequestDecorator

so this makes it harder to know what's happening and serialize summary of the workflow graph