# Quickstart: ACP Model Type Support

## Prerequisites

- ACP service endpoint available
- Standard multi_agent_ide configuration available

## Configure ACP Provider

1. Set the chat model provider to ACP in application configuration:

```properties
langchain4j.chat-model.provider=acp
```

2. Configure ACP transport settings:

```properties
langchain4j.acp.transport=stdio
langchain4j.acp.command=codex
langchain4j.acp.args=--experimental-acp
langchain4j.acp.working-directory=/path/to/workdir
```

3. Start the multi_agent_ide application and run a baseline workflow.

## Switch Back to HTTP Provider

```properties
langchain4j.chat-model.provider=http
```
