# Spec: Execution Artifacts, Templates, and Semantic Representations

## 1. Purpose and Goals

This specification defines the **canonical data model** for capturing, reconstructing, and analyzing executions in the agentic system.

The model is designed to:

* Preserve **full reconstructability** of any execution
* Support **post-hoc semantic analysis** (difficulty, success, similarity, indexing)
* Enable **long-term system improvement** (template evolution, loop evolution, controller learning)
* Remain **event-driven and effortless to emit**
* Avoid premature coupling between runtime execution and analysis

This spec explicitly separates:

* **Source artifacts** (what actually happened)
* **Semantic representations** (interpretations, indexes, summaries computed later)

---

## 2. Core Principles and Invariants

### 2.1 Canonical Execution Record

* Every run produces a single **Execution artifact** as its root.
* The Execution artifact contains a **tree of artifacts** that fully captures:

    * prompts
    * templates
    * arguments
    * messages
    * tool I/O
    * configuration
    * outcomes

The execution tree is the **authoritative source of truth**.

---

### 2.2 Hierarchical, Time-Sortable Artifact Identity

Each artifact has an `ArtifactKey` with the following properties:

* **Hierarchical**: child keys extend parent keys (prefix rule)
* **Time-sortable**: keys are lexicographically ordered by creation time
* **Decentralized**: keys can be generated independently by emitters

Example (conceptual):

```
ak:<execution-root>
 └── <prompt-render>
     ├── <template>
     └── <args>
```

**Invariant**
If artifact `B` is a child of artifact `A`, then:

```
B.key startsWith A.key
```

This tree structure defines:

* containment
* temporal ordering
* implicit provenance

---

### 2.3 Content Identity via Naive Hashing

Artifacts that carry bytes may define a `contentHash`, computed naively from canonical bytes.

* Templates **must** be static text
* Template version identity **is the hash of the static text**
* Hashing is used for:

    * deduplication
    * semantic caching
    * evolution tracking

The hierarchical key captures *where/when*; the hash captures *what*.

---

## 3. Execution Artifact Contract

### 3.1 Execution Artifact

The `Execution` artifact is the root of a run and must contain:

* All prompts and rendered messages
* All template definitions used
* All arguments/bindings
* All tool inputs and outputs
* All configuration state required to reproduce behavior
* All outcome evidence

**Required children (structural, not semantic):**

* `ExecutionConfig`
* `InputArtifacts`
* `AgentExecutionArtifacts`
* `OutcomeEvidenceArtifacts`

---

### 3.2 ExecutionConfig Artifact (Reconstructability-Critical)

An `ExecutionConfig` artifact **must exist** and must capture all non-textual control state, including:

* model identifiers and parameters
* tool availability and limits
* planner / routing parameters
* loop builder parameters
* repository snapshot identifiers (commit SHA, etc.)

This artifact ensures that behavior is reconstructable even if prompts change in the future.

---

## 4. Templates and Hierarchical Static IDs

### 4.1 Template as Artifact + Capability

Templates are **source artifacts** with the following properties:

* Static text (no runtime substitutions)
* Naively hashable
* Have a **static template ID** that does not change across versions

Templates are emitted **inside the execution tree**, typically as children of rendered prompts.

---

### 4.2 Hierarchical Template Static IDs (Key Insight)

Template static IDs are **hierarchical and referential**, not flat strings.

Example:

```
tpl.agent.discovery.prompt
tpl.agent.discovery.prompt.v1
tpl.agent.ticket.loop_builder.system
tpl.agent.ticket.loop_builder.system.constraints
```

This hierarchy is **semantic**, not temporal.

**Capabilities enabled:**

* Indexing “all templates under `tpl.agent.discovery.*`”
* Attaching semantic representations to:

    * an entire template subtree
    * a family of related templates
* Analyzing evolution across structured configuration dimensions

This same pattern applies to:

* plan templates
* loop templates
* policy templates

**Invariant**
Template evolution is tracked by:

```
(templateStaticId, templateContentHash)
```

---

### 4.3 Template Tree Structure in Execution

A rendered prompt artifact typically has this structure:

```
RenderedPrompt
 ├── PromptTemplate   (static text, static ID, hash)
 └── PromptArgs       (canonical JSON)
```

The rendered prompt artifact itself contains the **full rendered text**.

This guarantees:

* Full reconstructability
* No hidden dependencies
* Clear provenance of every token

---

## 5. Dependencies and Provenance (Tree-Only Model)

This system does **not** require a separate link graph.

Instead, dependencies must be represented by **containment or explicit references** inside the tree.

### 5.1 Dependency Rule

If artifact `B` depends on artifact `A`, then one of the following must be true:

* `A` is a child of `B`
* `B` contains a child `RefArtifact` referencing `A.key`
* `A` is embedded (quoted) in `B`’s bytes

This rule ensures dependencies are reconstructable without inference.

---

## 6. Semantic Representations (Post-Hoc Analysis Layer)

### 6.1 SemanticRepresentation

A `SemanticRepresentation` is computed **after execution** and attaches to an artifact by reference.

It never mutates source artifacts.

Fields:

* target artifact key
* derivation recipe ID + version
* model reference
* timestamp
* quality metadata
* payloads

---

### 6.2 Sealed Semantic Payload ADT

Initial payload variants:

* `Embedding`
* `Summary`
* `SemanticIndexRef`

Future additions may include:

* difficulty estimates
* success scores
* clustering labels
* failure classifications

---

### 6.3 SemanticIndexRef (Indexing as Semantics)

A `SemanticIndexRef` represents the insertion of an artifact into an index (vector, lexical, or structural).

This enables:

* retrieval
* similarity search
* directory-style browsing of templates/configs

Indexes are derived artifacts, not source state.

---

## 7. Outcomes, Difficulty, and Success

### 7.1 Source Outcome Evidence

The execution tree must include **objective outcome evidence**, such as:

* test results
* build results
* merge results
* explicit human acknowledgments

These are source artifacts.

---

### 7.2 Outcome Interpretation as Semantics

The following are **semantic representations**, not source:

* ticket difficulty
* overall success/failure
* graded outcome scores
* difficulty-adjusted performance

This allows reinterpretation as models improve.

---

## 8. What This Model Guarantees

If this spec is followed, the system guarantees:

1. Any execution can be fully reconstructed
2. Any prompt or decision can be reinterpreted under new semantics
3. Template and configuration evolution can be analyzed structurally
4. Semantic indexes can be built across hierarchical dimensions
5. No future learning objective requires re-instrumentation

---

## 9. Design Summary (Why This Is Sufficient)

* The **execution artifact tree** captures *everything that happened*
* **Hierarchical static IDs** capture *what kind of thing it was*
* **Hashes** capture *exact version identity*
* **Semantic representations** capture *what we later think about it*

This is the minimum structure that supports:

* evolution
* indexing
* learning
* replay
* and long-term system improvement

without overfitting the runtime to today’s analysis needs.

---

If you want next steps, I can:

* derive a **compliance checklist per agent stage**
* produce **Java/Kotlin interfaces** exactly matching this spec
* define **semantic job contracts** (inputs/outputs)
* or help you design the **directory-style template browser** enabled by hierarchical static IDs
