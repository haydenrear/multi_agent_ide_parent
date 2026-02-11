# Retain and Retain Batch

In the prompt for hindsight in ide, additionally, document_id needs special care because memory gets overriden by 
document_id. So we can't just have one document_id per file - in this case we'll use retain_batch. 

So when a memory is saved for a file, the agent will want to retrieve all of the previous memories for that file first,
and the reason being first and foremost that the previous memory may be irrelevant. Also, the previous memories can 
be put into context. However, the agent will still when they retain the memory batch for that file preserve the time
of the memories they're not changing.

```markdown
To preserve the original mentioned_at when re-retaining an old memory, provide the original timestamp as event_date 
in the retain call so it becomes the new mentioned_at
```

This is important so that we retain the temporal nature of the memories and update deprecated memories through the
evolution of the repo.

The AI will also want to update tags, mental models, and memory banks when updating memories relating to a change to 
a file. So this is important and speeds things up when we're doing discovery and code maps, and discovery collector
for the code maps and organization of the mental models for the code.

---

# Memory Bank and Mental Model

Need to add info about when to create mental models, banks, and tags, because that's where the reflect saves information 
about the graph in the prompt for hindsight - and how to integrate sources into this process for versioning and 
temporality.

Create_mental_model and refresh_mental_model need to be added as MCP tools as well, as this is where the creation of the
connections happens. The agent should understand that some amount of back and forth with this will be necessary to 
validate that the hindsight model is properly encoding memories in a valuable way. 

```markdown
Quick Steps
Create banks with a mission: Use create_bank and set a mission to guide mental model generation .
Retain with organization: Use document_id for versioning and tags for scoping; consolidation can auto-refresh tagged mental models consolidator.py:310-402 .
Create mental models: Call create_mental_model with a source_query that defines what to consolidate; enable refresh_after_consolidation to keep it current memory_engine.py:4608-4688 .
Guide AI via MCP: Set HINDSIGHT_API_MCP_INSTRUCTIONS to prompt the AI to create/query mental models before answering .
Use AI SDK tools: The createMentalModel and queryMentalModel tools already include descriptions encouraging consolidation and retrieval index.ts:449-483 .
Detailed Guidance
1. Organize memories into banks
Create a bank per project/user with a clear mission to bias mental model content.
Example via Python client:
client.banks.create(bank_id="proj-alpha", name="Project Alpha", mission="Summarize architecture, decisions, and user preferences")
2. Retain with structure
Use document_id to version documents and tags to scope memories.
Example:
client.retain(  
    bank_id="proj-alpha",  
    content="Updated auth flow to use OAuth2",  
    document_id="auth-flow.md",  
    tags=["auth", "decision"]  
)
3. Create mental models to organize memories
Define a source_query that captures the domain you want synthesized.
Enable refresh_after_consolidation so the model updates when new memories arrive test_consolidation.py:1755-1765 .
Example:
client.create_mental_model(  
    bank_id="proj-alpha",  
    name="Architecture Overview",  
    source_query="Summarize the current system architecture and recent changes",  
    tags=["architecture"],  
    trigger={"refresh_after_consolidation": True}  
)
4. Prompt AI to use mental models
Via MCP installation:
curl -fsSL https://hindsight.vectorize.io/get-mcp | bash -s -- \  
  --set HINDSIGHT_API_MCP_INSTRUCTIONS="Before answering, create or query a mental model to ensure consistent context."
The AI SDK tools already include guidance to consolidate and query mental models index.ts:449-483 .
5. Auto-refresh via consolidation
When you retain new memories with tags, consolidation will automatically refresh mental models with overlapping tags if refresh_after_consolidation is true consolidator.py:338-356 .
This keeps mental models current without manual intervention.
Notes
Mental models are stored in the mental_models table and as memory_units with fact_type='mental_model' for recall .
Use list_mental_models to discover existing models and get_mental_model to retrieve content api_namespaces.py:76-84 .
Tag-based scoping prevents cross-tenant leakage during refresh memory_engine.py:4720-4724 .
```

# Consider Sandboxed Python or Cli in Place of MCP

An easy way to manage this - and especially because MCP becomes a bit of a headache to manage the versioning, would be
to prompt to ask it to just generate python code that calls the server, or else expose the CLI.

# Consider Minimum Memory Detection and Prompt Injections

Another important thing is that the model will often skip saving/retaining/querying memories because it won't know to
do it. And then it needs special querying to do it - we can introduce a prompt that is added upon detecting none of the
tool calls. In particular, hindsight tool calls should come in groups. They should recall, reflect, then do some 
research/work then retain. There are especially agents that should be reflecting and recalling a lot, such as discovery
and planning, and agents that should be retaining and performing memory bank work a lot, such as ticket. So we can 
look in the BlackboardHistory under that agent's ArchiveKey and count the number, and then tell them that they must 
use the memory bank and refine their answer. 

# Memory Agent Traces 

By including `include.tool_calls` in the reflect, then the tool calls can be captured and in the response we can parse
it in the ToolCallRenderer and emit it under the artifact key of the caller (which will be parent.parent of the tool call)
as an artifact. So then we can extract these artifacts and tune our trace model as well.


# Prompt Versioning of Agent

In the fork, the prompt of the reflect agent will be able to be provided as an ENV path. This will help because we can
version that prompt also. Then when building the artifacts we add this ref so the manager models can take note of 
how to evolve this agent prompt.

```markdown
You cannot directly replace the entire reflect agent prompt via API parameters. However, you can shape the prompt through several mechanisms: bank mission/disposition, additional context, and directives. The prompt is assembled by build_system_prompt_for_tools in prompts.py using these inputs prompts.py:124-339 . The reflect agent then uses this system prompt when calling the LLM with tools agent.py:325-333 .

The standalone reflect function in think_utils.py uses a different prompt path (build_think_prompt/get_system_message), but this is not the agentic reflect endpoint think_utils.py:103-192 .
No environment variable exists to override the entire prompt; only settings like HINDSIGHT_API_REFLECT_MAX_ITERATIONS are exposed configuration.md:493-497 .
```

---

It would be cool if the reflect agent had tools for repo, especially intellij which has file ops - and then it can do
validation on the repo. Also should be able to 

```python
# In register_mcp_tools, the tools set controls which tools are registered mcp_tools.py:122-137 . To add a new tool (e.g., a file reader), define it with FastMCP and include it in the set:

@mcp.tool()  
async def read_repo_file(path: str) -> str:  
    """Read a file from the project repository."""  
    # Implementation to read and return file content  
    return content  
  
# Then register it  
config = MCPToolsConfig(  
    bank_id_resolver=get_current_bank_id,  
    tools={"retain", "recall", "reflect", "read_repo_file"},  # Add your tool  
)  
register_mcp_tools(mcp, memory, config)
```

This is mostly integrated already, but additional tools can be added easily but needs fork.

So this can be branched - and then we'll add Intellij tool and make sure that we ask the agent to always save the
Intellij project as one of the banks so the agent knows how to search and check and update the prompt for the hindsight
agent - however probably this can be pushed off as it's not really needed - but could be experimented with.

---

