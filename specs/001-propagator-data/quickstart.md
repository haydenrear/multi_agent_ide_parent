# Quickstart: Controller Data Propagators and Transformers

**Feature**: `001-propagator-data`  
**Branch**: `001-propagator-data`

## 1. Open the feature artifacts

Review the generated design files before implementation:

- `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/spec.md`
- `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/plan.md`
- `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/research.md`
- `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/data-model.md`
- `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/contracts/controller-data-flow.openapi.yaml`

## 2. Implementation order

1. Add shared propagator types under `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/propagation/`.
2. Add shared transformer types under `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/transformation/`.
3. Add app-layer registration, discovery, execution, and repository support under `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/` and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/`.
4. Integrate propagators with action request/response flows.
5. Integrate transformers with controller endpoint response flows.
6. Add propagation and transformation record querying plus propagation-item resolution support.

## 3. Validate the modules compile

From the repo root:

```bash
cd /Users/hayde/IdeaProjects/multi_agent_ide_parent
./multi_agent_ide_java_parent/gradlew \
  :multi_agent_ide_java_parent:multi_agent_ide_lib:compileJava \
  :multi_agent_ide_java_parent:multi_agent_ide:compileJava
```

## 4. Run the core test suite for the app module

From `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent`:

```bash
cd /Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent
./gradlew :multi_agent_ide_java_parent:multi_agent_ide:test --info
```

`test_graph` coverage is intentionally deferred for this feature.

## 5. Contract-first validation examples

List attachable propagator targets:

```bash
curl -s http://localhost:8080/api/propagators/attachables
```

List attachable transformer targets:

```bash
curl -s http://localhost:8080/api/transformers/attachables
```

Register a propagator (replace the example `layerId` with a real action-layer id returned from the attachables endpoint):

```bash
curl -s -X POST http://localhost:8080/api/propagators/registrations \
  -H 'Content-Type: application/json' \
  -d @/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/examples/register-propagator.json
```

Register a transformer (replace the example `layerId` with a real controller-endpoint layer id returned from the attachables endpoint):

```bash
curl -s -X POST http://localhost:8080/api/transformers/registrations \
  -H 'Content-Type: application/json' \
  -d @/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/examples/register-transformer.json
```

Resolve an acknowledgement-required propagation item:

```bash
curl -s -X POST http://localhost:8080/api/propagations/items/ITEM_ID/resolve \
  -H 'Content-Type: application/json' \
  -d '{"resolutionType":"ACKNOWLEDGED","resolutionNotes":"Controller acknowledged the propagated information"}'
```

## 6. Suggested implementation checkpoints

- Shared propagator and transformer models compile in `multi_agent_ide_lib`.
- App-layer registration and persistence compile in `multi_agent_ide`.
- A response-stage propagator emits a `PropagationEvent` and can create an acknowledgement-required `PropagationItem`.
- A controller-endpoint transformer emits a `TransformationEvent` and returns a transformed string.
- `PROPAGATION_ACKNOWLEDGED` is emitted when the controller acknowledges a propagation item through the permission gate flow.
