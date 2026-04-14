rootProject.name = "multi_agent_ide_parent"

include(":multi_agent_ide_python_parent:packages:view_agents_utils")

project(":multi_agent_ide_python_parent:packages:view_agents_utils").projectDir = file(layout.settingsDirectory.dir("multi_agent_ide_python_parent/packages/view_agents_utils"))

include(":multi_agent_ide_python_parent:packages:view_agent_exec")

project(":multi_agent_ide_python_parent:packages:view_agent_exec").projectDir = file(layout.settingsDirectory.dir("multi_agent_ide_python_parent/packages/view_agent_exec"))

include(":multi_agent_ide_java_parent:multi_agent_ide")
include(":multi_agent_ide_java_parent:multi_agent_ide_lib")
include(":multi_agent_ide_java_parent:acp-cdc-ai")
include(":multi_agent_ide_java_parent:utilitymodule")
include(":tracing_agent")
include(":tracing_aspect")
include(":runner_code")
include(":graphql")
include(":test_graph")
include(":persistence")
include(":jpa-persistence")
include(":tracing")
include(":commit-diff-context")
include(":commit-diff-model")
include(":mcp-tool-gateway")
include(":libs-resolver")
include(":test-mcp-server")
include(":proto")
include(":hindsight")
