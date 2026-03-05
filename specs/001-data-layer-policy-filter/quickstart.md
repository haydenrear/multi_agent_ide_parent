# Quickstart: Layered Data Policy Filtering

**Feature**: 001-data-layer-policy-filter  
**Date**: 2026-03-01

## Overview

This guide shows how to:

1. Discover active policies for a layer.

## Endpoint Summary

Each concrete filter type has its own registration endpoint. The filter type, I/O types, and interpreter (for path filters) are implicit from the endpoint — no `filter` block or `inputType`/`outputType` fields needed in the request body.

| Filter Type | Endpoint | I/O |
|---|---|---|
| JsonPathFilter | `POST /api/filters/json-path-filters/policies` | JsonDocument/JsonDocument |
| MarkdownPathFilter | `POST /api/filters/markdown-path-filters/policies` | MarkdownDocument/MarkdownDocument |
| RegexPathFilter | `POST /api/filters/regex-path-filters/policies` | String/String |
| AiPathFilter | `POST /api/filters/ai-path-filters/policies` | String/String (AI instruction execution) |

AI filter registration uses the dedicated request schema:
- `contracts/ai-policy-registration-request.schema.json`

## Layer Binding Matchers

Each `layerBinding` includes matcher fields that narrow which specific `PromptContributor` or `GraphEvent` instances the filter applies to within the bound layer.

| Field | Values | Description |
|---|---|---|
| `matcherKey` | `NAME`, `TEXT` | What to match on (name/class name vs. output text/payload) |
| `matcherType` | `REGEX`, `EQUALS` | How to match (regex pattern vs. exact equality) |
| `matcherText` | string | The regex expression or exact text |
| `matchOn` | `PROMPT_CONTRIBUTOR`, `GRAPH_EVENT` | Which domain object to match against |

**Constraints**: Path and AI filter endpoints accept either `PROMPT_CONTRIBUTOR` or `GRAPH_EVENT` match scopes.

## 1. Discover Active Policies by Layer

```http
GET /api/filters/layers/{layerId}/policies?status=ACTIVE
```

Expected behavior:
- Returns active policies only (across all filter types bound to that layer).
- Each policy includes `description`, `sourcePath`, and `filterType` discriminator.

## 5. Register a JsonPathFilter with BinaryExecutor

```http
POST /api/filters/json-path-filters/policies
Content-Type: application/json

{
  "name": "drop-sensitive-json-fields",
  "description": "Removes noisy/sensitive JSON fields using path instructions",
  "sourcePath": "dynamic://filters/bin/json-path-ops",
  "priority": 75,
  "isInheritable": false,
  "isPropagatedToParent": true,
  "layerBindings": [
    {
      "layerId": "layer-workflow-agent-ticket-agent",
      "layerType": "WORKFLOW_AGENT",
      "layerKey": "workflow-agent/ticket-agent",
      "enabled": true,
      "includeDescendants": true,
      "isInheritable": true,
      "isPropagatedToParent": true,
      "matcherKey": "NAME",
      "matcherType": "REGEX",
      "matcherText": ".*",
      "matchOn": "GRAPH_EVENT"
    }
  ],
  "executor": {
    "executorType": "BINARY",
    "command": ["/usr/local/bin/json-path-ops", "--mode", "policy"],
    "workingDirectory": null,
    "env": null,
    "outputParserRef": "instruction-list-v1",
    "timeoutMs": 500
  },
  "activate": true
}
```

Expected behavior:
- Binary executor returns ordered `Replace`/`Set`/`Remove` instructions.
- JsonPathInterpreter is implicit — no need to specify it in the request.
- Interpreter applies instructions deterministically.
- PathFilter endpoints accept either `PROMPT_CONTRIBUTOR` or `GRAPH_EVENT` for `matchOn`.

## 6. Deactivate a Policy (Global)

Globally deactivates a policy across all layers. The policy history is preserved.

```http
POST /api/filters/policies/{policyId}/deactivate
```

## 7. Disable a Policy at a Specific Layer

Disables a policy at one layer without affecting other layer bindings. Optionally propagates to descendant layers.

```http
POST /api/filters/policies/{policyId}/layers/{layerId}/disable
Content-Type: application/json

{
  "includeDescendants": false
}
```

Expected behavior:
- Sets `enabled=false` on the binding for `{layerId}` only.
- If `includeDescendants=true`, also disables at all descendant layers.
- Response includes the number of affected descendant layers.
- The policy remains active at other layers.

## 8. Re-enable a Policy at a Specific Layer

```http
POST /api/filters/policies/{policyId}/layers/{layerId}/enable
Content-Type: application/json

{
  "includeDescendants": false
}
```

## 9. Update a Policy Layer Binding (Full Upsert)

For full control over a layer binding (changing matcher, propagation settings, etc.), use the PUT endpoint.

```http
PUT /api/filters/policies/{policyId}/layers
Content-Type: application/json

{
  "policyId": "policy-123",
  "layerBinding": {
    "layerId": "layer-workflow-agent-ticket-agent",
    "layerType": "WORKFLOW_AGENT",
    "layerKey": "workflow-agent/ticket-agent",
    "enabled": false,
    "includeDescendants": false,
    "isInheritable": false,
    "isPropagatedToParent": false,
    "matcherKey": "NAME",
    "matcherType": "EQUALS",
    "matcherText": "NodeStatusChanged",
    "matchOn": "GRAPH_EVENT"
  }
}
```

## 10. Discover Child Layers Before Policy Mutation

```http
GET /api/filters/layers/{layerId}/children?recursive=true
```

## 11. Inspect Filtered Output by Policy

```http
GET /api/filters/policies/{policyId}/layers/{layerId}/records/recent?limit=50&cursor=
```

Expected behavior:
- Returns policy-scoped records only.
- Each record includes `filterType` discriminator, applied instructions, and transformed output metadata.

## Validation Checklist

- Register valid Java function, python, binary, and AI executors via per-type endpoints.
- Confirm invalid requests to wrong filter-type endpoints are rejected.
- Confirm `matchOn` constraint enforcement (semantic validation): `PROMPT_CONTRIBUTOR` only for prompt-contributor endpoints, `GRAPH_EVENT` only for event endpoints, either for path filter endpoints.
- Confirm deactivated policies stop affecting new payloads.
- Confirm layer-scoped disable: policy disabled at one layer still applies at other layers.
- Confirm layer-scoped disable with `includeDescendants=true` propagates to child layers.
- Confirm policy-scoped output inspection excludes other policies.

## Test Execution

```shell
./gradlew multi_agent_ide_java_parent:multi_agent_ide:test
./gradlew multi_agent_ide_java_parent:multi_agent_ide_lib:test
```

Note: `test_graph` scenarios are intentionally deferred for this feature.
