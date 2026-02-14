# test_graph Deferred Mapping

`test_graph` implementation is intentionally deferred for this feature.

## Spec Tags
- `@ui_abstraction_parity`
- `@script_interface`
- `@workflow_context_docs`

## Deferred Mapping Plan
- Add feature files in `test_graph/src/test/resources/features/llm_debug_ui/` in a follow-up ticket.
- Reuse a small generic step-definition set focused on:
  - initial application state and required services/config
  - action invocation through shared UI or scripts
  - expected message/event evidence
  - workflow/routing context interpretation checks

## Reason
Stakeholder requested deferral for this ticket to focus on UI abstraction, script interface, and prompt-context documentation first.
