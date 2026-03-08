# Quickstart: Runtime ACP Model Selection

## 1. Configure a provider catalog

Define multiple named ACP providers and one default provider in application configuration.
The implementation now uses a catalog shape under `multi-agent-embabel.acp`:

```yaml
multi-agent-embabel:
  acp:
    default-provider: codex
    providers:
      codex:
        transport: stdio
        command: ${ACP_COMMAND:codex-acp}
        args: ${ACP_ARGS:--sandbox workspace-write}
        working-directory: ${ACP_WORKING_DIR:}
        auth-method: chatgpt
      claudeopenrouter:
        transport: stdio
        command: ${ACP_COMMAND:claude-agent-acp}
        args: ${ACP_ARGS:--model openrouter/free}
        default-model: openrouter/free
        env:
          ANTHROPIC_BASE_URL: https://openrouter.ai/api
```

## 2. Build the runtime call payload

For each LLM call, construct a versioned `AcpChatOptionsString` payload with:

- the session/artifact key used for memory and event routing
- the requested ACP provider (optional when defaulting)
- the requested model (optional when the selected provider can default it)
- any additional call-scoped options

## 3. Encode the payload for the current LLM transport path

Serialize the payload and place it into the model transport string as:

```text
sessionIdentity___jsonPayload
```

This preserves current session extraction while enabling richer runtime routing.

## 4. Resolve the provider before ACP session startup

At the ACP integration boundary:

1. Parse the encoded payload.
2. Resolve the selected or default provider from the provider catalog.
3. Compute the effective model.
4. Freeze the resolved provider definition for the call.
5. Build a composite session-routing key for session create/reuse decisions.

## 5. Verify expected behavior

Validate the following flows:

- one running application can execute calls against at least two ACP providers
- a call without an explicit provider uses the configured default provider
- unknown providers and malformed payloads fail before ACP session startup
- two concurrent calls with different providers do not reuse the same ACP session incorrectly
- logs/diagnostics show session identity, resolved provider, and effective model without exposing secrets

## 6. Regression targets

Focus regression coverage on:

- `DefaultLlmRunner` payload construction
- `MultiAgentEmbabelConfig` option conversion
- `AcpModelProperties` catalog binding and default resolution
- `AcpChatModel` payload parsing, provider resolution, sandbox translation, and session cache keying

Suggested verification commands:

```bash
cd /Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent
./gradlew :multi_agent_ide_java_parent:acp-cdc-ai:test --tests com.hayden.acp_cdc_ai.acp.AcpChatModelArgsParsingTest
./gradlew :multi_agent_ide_java_parent:multi_agent_ide:test --tests com.hayden.multiagentide.service.DefaultLlmRunnerTest
```
