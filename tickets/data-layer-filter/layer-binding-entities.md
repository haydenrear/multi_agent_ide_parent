# Layer Binding Entity Extraction

## Problem

Layer bindings across all three data-layer systems (filter policies, propagators, transformers) are stored as a JSON blob (`layerBindingsJson TEXT`) inside their parent registration entity. This causes several problems:

1. **No individual identity** — bindings have no primary key or unique identifier. You cannot activate/deactivate a single binding; you can only operate on the whole registration or do a full JSON rewrite.
2. **No direct querying** — finding which registrations are bound to a specific layer requires `LIKE '%layerId%'` queries against JSON text (see `PolicyRegistrationRepository.findActiveByLayerId`), which is fragile and unindexed.
3. **Inconsistent CRUD across systems** — the three systems have different API surfaces for managing bindings:
   - **Filter (PolicyController)**: has `enable`, `disable`, and `update` endpoints for layer bindings, but they operate by rewriting the JSON blob and identifying bindings by `layerId` match (not a binding ID)
   - **Propagator (PropagatorController)**: has a `PUT /layers` endpoint that appends/replaces a binding by `layerId`, but no `DELETE`, no individual enable/disable
   - **Transformer (TransformerController)**: has a `PUT /layers` endpoint mirroring propagator, same limitations
4. **Duplicate binding risk** — nothing prevents two bindings with the same `layerId` from existing in the JSON array since there's no unique constraint
5. **No audit trail per binding** — activation/deactivation timestamps exist on the registration entity, not on individual bindings

## Current State

### Storage (all three identical pattern)
```
registration_entity
  ├── registrationId (PK, unique)
  ├── status (ACTIVE/INACTIVE — whole registration)
  ├── layerBindingsJson (TEXT — serialized List<XxxLayerBinding>)
  └── ...
```

### Model records (in multi_agent_ide_lib)
- `PolicyLayerBinding` — layerId, enabled, includeDescendants, isInheritable, isPropagatedToParent, matcherKey, matcherType, matcherText, matchOn, updatedBy, updatedAt
- `PropagatorLayerBinding` — same fields
- `TransformerLayerBinding` — layerId, enabled, includeDescendants, isInheritable, isPropagatedToParent, matcherKey, matcherType, matcherText, matchOn

### API comparison

| Operation | Filter | Propagator | Transformer |
|-----------|--------|------------|-------------|
| Register (with bindings) | POST /policies | POST /registrations | POST /registrations |
| List by layer | POST /layers/policies | POST /registrations/by-layer | POST /registrations/by-layer |
| Add/update binding | POST /policies/layers/update | PUT /registrations/{id}/layers | PUT /registrations/{id}/layers |
| Enable binding at layer | POST /policies/layers/enable | — | — |
| Disable binding at layer | POST /policies/layers/disable | — | — |
| Delete binding | — | — | — |
| List bindings for registration | — | — | — |
| Activate registration | POST /policies/activate (missing) | POST /registrations/{id}/activate | — |
| Deactivate registration | POST /policies/deactivate | POST /registrations/{id}/deactivate | POST /registrations/{id}/deactivate |

## Proposed Solution

### 1. Extract layer bindings into their own JPA entity

Create a shared or per-system `LayerBindingEntity` table with a foreign key back to the parent registration:

```
layer_binding
  ├── id (PK, auto)
  ├── bindingId (unique, e.g. "binding-<uuid>")
  ├── registrationId (FK → registration)
  ├── registrationType (FILTER / PROPAGATOR / TRANSFORMER)
  ├── layerId
  ├── enabled (boolean)
  ├── includeDescendants
  ├── isInheritable
  ├── isPropagatedToParent
  ├── matcherKey
  ├── matcherType
  ├── matcherText
  ├── matchOn
  ├── updatedBy
  ├── updatedAt
  ├── createdAt
  └── UNIQUE(registrationId, layerId)  -- prevent duplicates
```

This gives each binding a stable identity (`bindingId`) for individual CRUD operations.

### 2. Standardize the API across all three systems

Every system should expose the same binding management endpoints:

```
POST   /api/{system}/registrations/{registrationId}/bindings          — add a binding
GET    /api/{system}/registrations/{registrationId}/bindings          — list bindings for a registration
POST   /api/{system}/bindings/{bindingId}/enable                      — enable a single binding
POST   /api/{system}/bindings/{bindingId}/disable                     — disable a single binding
DELETE /api/{system}/bindings/{bindingId}                             — remove a binding
PUT    /api/{system}/bindings/{bindingId}                             — update binding config (matcher, etc.)
```

Where `{system}` is `filters`, `propagators`, or `transformers`.

### 3. Migrate existing data

- Add Liquibase changeset to create the `layer_binding` table
- Write a migration step (ApplicationRunner or changeset with custom SQL) to parse existing `layerBindingsJson` blobs into rows
- Keep `layerBindingsJson` column temporarily for rollback safety, drop in a follow-up

### 4. Update discovery services

Replace JSON deserialization in `PropagatorDiscoveryService`, `TransformerDiscoveryService`, and `PolicyRegistrationService` with JPA joins/queries against the new entity.

## Files Affected

- **Entities**: new `LayerBindingEntity`; modify `PropagatorRegistrationEntity`, `TransformerRegistrationEntity`, `PolicyRegistrationEntity` to add `@OneToMany`
- **Repositories**: new `LayerBindingRepository`; update existing repos to remove JSON-based queries
- **Services**: `PropagatorRegistrationService`, `TransformerRegistrationService`, `PolicyRegistrationService`, `PropagatorDiscoveryService`, `TransformerDiscoveryService`
- **Controllers**: `PropagatorController`, `TransformerController`, `FilterPolicyController` — add standardized binding endpoints
- **DTOs**: new binding request/response DTOs (can be shared or per-system)
- **Liquibase**: new changeset for `layer_binding` table + migration
- **Lib models**: `PropagatorLayerBinding`, `TransformerLayerBinding`, `PolicyLayerBinding` — keep as API models, decouple from persistence
- **Tests**: `PropagatorPersistenceIT`, `FilterPersistenceIT`, transformer tests
