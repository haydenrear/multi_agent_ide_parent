```prompt
So what I'm thinking is that we handle this the way we handle progressive disclosure in paths. For instance, we can disclose via paths, search via paths. We may have multiple levels of granularity there to help even more with progressive disclosure. And then we can do it simply as well, with tool tool. We can even be smart and deny a tool tool call if we don't have a matching schema call for that tool in that session.
```

```markdown
Yes — that is the direction I would take.

What you are describing is basically **progressive disclosure over a virtual tool namespace**, where the visible MCP surface stays fixed and the real capability space is explored through path-like traversal and late schema reveal. That fits the protocol reality better than trying to force mid-session first-class tool mutation through hosts that may cache or flatten tool lists aggressively. MCP does support `tools/list_changed`, and Claude Code documents that it refreshes capabilities when that notification arrives, but real-world implementations have still had refresh and cache issues in some cases. ([Model Context Protocol][1])

The strongest part of your refinement is that you are no longer thinking of this as only a “generic tool call.” You are treating tools more like a **discoverable tree or path space**, similar to resources or rooted path disclosure. MCP already has a concept of client roots and change notifications around them, so using path-structured disclosure as your mental model is very natural even though your inner skill registry is not itself a filesystem. ([Model Context Protocol][2])

I think the session rule you proposed is especially good:

> deny a `tool tool` call unless there was a matching schema/discovery call for that tool in the same session.

That gives you three things at once.

First, it forces a deliberate reveal step, which preserves progressive disclosure.

Second, it gives you a server-side audit trail that the model saw the current schema before invocation.

Third, it protects you from stale assumptions when the host’s cached outer tool list is stable but your inner registry is evolving.

That is not something MCP gives you automatically for virtual tools; it is a policy layer you are adding on top of the protocol, and it is exactly the sort of thing a gateway should do.

I would tighten it slightly, though. I would not require only “matching schema call happened sometime in this session.” I would require a **schema lease** or **schema token**.

A stronger version looks like this:

* `search_tools(path, query, depth)` returns candidate virtual tools
* `get_tool_schema(tool_path)` returns:

  * canonical tool id
  * schema
  * schema digest
  * issued_at
  * maybe expires_at
  * maybe session-scoped invocation token
* `call_tool(tool_id, args, schema_digest or token)` is accepted only if:

  * the tool was disclosed in this session
  * the digest still matches current schema
  * or the issued token is still valid

That is better than just “a prior schema call exists,” because otherwise the model can discover schema once early in a long session and call it much later after the backing tool changed.

So I would implement your denial rule as:

**No invocation without prior disclosure in-session, and no invocation against a stale schema version.**

That gives you deterministic behavior.

I would also separate **path browsing** from **schema reveal**. In other words:

* browsing/searching a path should be cheap and summary-only
* selecting a concrete tool should reveal schema
* invoking should require the revealed schema token or digest

That preserves the layered disclosure you want. It also prevents the “search” operation from becoming a hidden schema dump.

A good shape would be:

`browse_tools`

* accepts namespace path, depth, filters
* returns folders/categories and lightweight tool summaries

`search_tools`

* accepts semantic query plus optional namespace/path constraints
* returns ranked lightweight matches

`describe_tool`

* returns full schema, examples, side effects, and schema digest/token

`invoke_tool`

* requires prior `describe_tool` result by digest/token

That is cleaner than only `schema_tool` + `tool_tool`, because it lets you preserve different granularity levels exactly the way you described.

There is another reason I like your path idea: MCP already supports pagination for large list operations, and large dynamic spaces benefit from not returning everything at once. So a path- or namespace-oriented virtual registry can naturally paginate and disclose incrementally instead of exploding the host with a full flattened tool inventory. ([Model Context Protocol][3])

One thing I would be careful about: do not make the gating too strict in a way that hurts model flow. If the model has to do three bureaucratic calls every time, you may get degraded ergonomics. The right compromise is to let `search_tools` and `describe_tool` be very lightweight and make the denial error highly actionable, for example:

> Tool invocation denied. Call `describe_tool` for `x/y/z` first to obtain the current schema token.

That turns the policy into a guidance loop rather than a dead end.

I would also preserve **stable canonical names** for the virtual tools, even if their implementation is dynamic. You were right earlier that host-side tool selection is name-based after the outer MCP tool list is flattened. Since the host only ever sees your outer gateway tools, the inner canonical names now become your own contract, and they should be stable, path-like, and versionable.

So something like:

* `code/search/java/find_symbols`
* `code/gradle/list_tasks`
* `memory/hindsight/update_model`

is better than ad hoc names.

My honest take is that this is now becoming a strong design, not a workaround. The design principle is:

**Use a small stable MCP facade, and move progressive disclosure, schema reveal, and invocation authorization into a session-aware virtual tool registry.**

That gives you:

* compatibility with clients that do not refresh dynamic tools reliably,
* progressive disclosure by namespace/path,
* explicit schema-read-before-call semantics,
* and a clean place to enforce policy, ranking, and audit.

The only thing I would add is a formal notion of **disclosure state** in the session. Not just “did we call schema,” but a small session registry:

* disclosed paths
* disclosed tools
* schema digests issued
* optional expiration
* invocation counts / limits

That will make the behavior much easier to reason about when the system gets larger.

This is the one-line version of the policy I would implement:

> A virtual tool may be invoked only if it was explicitly disclosed in the current session and the invocation references the currently disclosed schema digest or disclosure token.

That gives you the matryoshka behavior you want without depending on fragile host-side `list_changed` handling.

[1]: https://modelcontextprotocol.io/specification/2025-11-25/server/tools?utm_source=chatgpt.com "Tools"
[2]: https://modelcontextprotocol.io/specification/2025-06-18/client/roots?utm_source=chatgpt.com "Roots"
[3]: https://modelcontextprotocol.io/specification/2025-11-25/server/utilities/pagination?utm_source=chatgpt.com "Pagination"

```