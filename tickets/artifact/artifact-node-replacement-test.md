We need to test the following cases:

- the event where an artifact node is loaded from the database, and is a reference node
- loaded from the database, a reference node, but then another one is added with same content hash, different children?
  - for the case where same content hash collision, but different children added now - this should produce an artifact ref with different children than the original artifact with hash, but it ends up adding the children, I think to the original? 

So additionally, we'll want to redesign the algorithm for flushing and saving (flush-db-endpoint.md, artifact-file-flushing.md),
with weak references.

Additionally, we have to remove children entirely from the ArtifactEntity - it makes no sense to have them there.

```markdown
Yes. The key fix is:

* persist each `ArtifactEntity.contentJson` **without children**
* treat tree topology as authoritative from:

  * `artifactKey`
  * `parentKey`
  * optionally `childIds` as cache/denormalization
* reconstruct `children` only when loading a tree/view

Then the weak-ref layer should cache only **materialized node state**, not the identity of the node itself.

Below is a concrete implementation sketch.

---

# 1. Persist payload-only JSON

The smallest safe change is to strip `children` before serialization, and normalize deserialized artifacts so `children` is never `null`.

## `ArtifactPersistenceCodec`

```java
package com.hayden.multiagentide.artifacts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ArtifactPersistenceCodec {

    private final ObjectMapper objectMapper;

    public ArtifactPersistenceCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Persist payload only. Tree topology is stored relationally, not in JSON.
     */
    public String toPayloadJson(Artifact artifact) throws JsonProcessingException {
        Artifact payloadOnly = withoutChildren(artifact);
        return objectMapper.writeValueAsString(payloadOnly);
    }

    /**
     * Reads payload-only JSON and normalizes children to an empty list.
     */
    public Artifact fromPayloadJson(String json) throws JsonProcessingException {
        Artifact artifact = objectMapper.readValue(json, Artifact.class);
        return normalizeChildren(artifact);
    }

    public Artifact withoutChildren(Artifact artifact) {
        if (artifact == null) {
            return null;
        }
        return artifact.withChildren(List.of());
    }

    public Artifact normalizeChildren(Artifact artifact) {
        if (artifact == null) {
            return null;
        }
        List<Artifact> children = artifact.children() == null ? List.of() : artifact.children();
        if (children == artifact.children()) {
            return artifact;
        }
        return artifact.withChildren(children);
    }
}
```

This alone fixes the double-source-of-truth problem.

---

# 2. Update `ArtifactService.toEntity(...)`

Change serialization so it never writes recursive children into `contentJson`.

## `ArtifactService` relevant changes

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ArtifactService {

    private final ArtifactRepository artifactRepository;
    private final ArtifactPersistenceCodec persistenceCodec;

    private ObjectMapper objectMapper;
    private EventBus eventBus;

    @Autowired
    @Lazy
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @PostConstruct
    public void configure() {
        var j = new Jackson2ObjectMapperBuilder();
        SerdesConfiguration.artifactAndAgentModelMixIn().customize(j);
        this.objectMapper = j.build();
    }

    Optional<ArtifactEntity> toEntity(String executionKey, Artifact artifact) {
        if (artifact == null || artifact.artifactKey() == null) {
            publishPersistenceError("Cannot convert null artifact or artifact key to entity", null, null);
            return Optional.empty();
        }

        ArtifactKey key = artifact.artifactKey();
        try {
            Artifact payloadOnly = persistenceCodec.withoutChildren(artifact);
            String contentJson = persistenceCodec.toPayloadJson(payloadOnly);
            String parentKey = key.parent().map(ArtifactKey::value).orElse(null);

            var templated = artifact instanceof Templated temp ? temp : null;
            var templateDbRef = artifact instanceof Artifact.TemplateDbRef temp ? temp : null;
            var artifactDbRef = artifact instanceof Artifact.ArtifactDbRef temp ? temp : null;

            if (templateDbRef != null && templateDbRef.ref() == null) {
                throw new IllegalArgumentException("Ref was null for TemplateDbRef provided.");
            }
            if (artifactDbRef != null && artifactDbRef.ref() == null) {
                throw new IllegalArgumentException("Ref was null for ArtifactDbRef provided.");
            }

            return Optional.of(ArtifactEntity.builder()
                    .artifactKey(key.value())
                    .referencedArtifactKey(
                            Optional.ofNullable(templateDbRef)
                                    .map(td -> td.ref().templateArtifactKey().value())
                                    .or(() -> Optional.ofNullable(artifactDbRef).map(td -> td.ref().artifactKey().value()))
                                    .orElse(null))
                    .templateStaticId(Optional.ofNullable(templated).map(Templated::templateStaticId).orElse(null))
                    .parentKey(parentKey)
                    .executionKey(executionKey)
                    .artifactType(artifact.artifactType())
                    .contentHash(artifact.contentHash().orElse(null))
                    .contentJson(contentJson)
                    .depth(key.depth())
                    .shared(false)
                    .childIds(
                            StreamUtil.toStream(artifact.children()).stream()
                                    .map(Artifact::artifactKey)
                                    .filter(Objects::nonNull)
                                    .map(ArtifactKey::value)
                                    .collect(Collectors.toCollection(ArrayList::new))
                    )
                    .build());
        } catch (Exception e) {
            publishPersistenceError("Failed to convert artifact to entity " + key.value(), key, e);
            return Optional.empty();
        }
    }

    public Optional<Artifact> deserializeArtifact(ArtifactEntity entity) {
        if (entity == null || entity.getContentJson() == null) {
            return Optional.empty();
        }

        try {
            Artifact artifact = persistenceCodec.fromPayloadJson(entity.getContentJson());

            artifact = switch (artifact) {
                case Artifact.TemplateDbRef t ->
                        this.artifactRepository.findByArtifactKey(entity.getReferencedArtifactKey())
                                .flatMap(this::deserializeArtifact)
                                .map(ae -> {
                                    if (ae instanceof Templated templated) {
                                        return t.toBuilder()
                                                .ref(templated)
                                                .children(List.of()) // always payload-only at this stage
                                                .artifactType(Artifact.TemplateDbRef.class.getSimpleName())
                                                .build();
                                    }

                                    log.error("Found artifact incompatible with templated {}.", t);
                                    return t.withChildren(List.of());
                                })
                                .orElseGet(() -> {
                                    log.error("Could not find referenced artifact in repository!");
                                    return t.withChildren(List.of());
                                });

                case Artifact.ArtifactDbRef t ->
                        this.artifactRepository.findByArtifactKey(entity.getReferencedArtifactKey())
                                .flatMap(this::deserializeArtifact)
                                .map(ae -> t.toBuilder()
                                        .ref(ae)
                                        .children(List.of()) // payload-only
                                        .artifactType(Artifact.ArtifactDbRef.class.getSimpleName())
                                        .build())
                                .orElseGet(() -> {
                                    log.error("Could not find referenced artifact in repository!");
                                    return t.withChildren(List.of());
                                });

                default -> artifact.withChildren(List.of());
            };

            log.debug("Successfully deserialized artifact payload-only: {} of type: {}",
                    entity.getArtifactKey(), entity.getArtifactType());
            return Optional.of(artifact);
        } catch (Exception e) {
            var err = "Failed to deserialize artifact: %s of type: %s"
                    .formatted(entity.getArtifactKey(), entity.getArtifactType());
            log.error(err, e);
            ArtifactKey artifactKey = null;
            try {
                artifactKey = new ArtifactKey(entity.getArtifactKey());
            } catch (IllegalArgumentException ignored) {
            }
            publishPersistenceError(e.getMessage(), artifactKey, e);
            return Optional.empty();
        }
    }

    // ... rest unchanged
}
```

## Why deserialization changes

It changes slightly:

* before: JSON implicitly contained subtree
* now: JSON contains only payload
* therefore deserialization must always return `children = List.of()`
* tree assembly happens elsewhere

That is the correct separation.

---

# 3. Add repository methods for topology-driven load

You need relational child lookup.

## `ArtifactRepository`

```java
package com.hayden.multiagentide.artifacts.repository;

import com.hayden.multiagentide.artifacts.entity.ArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ArtifactRepository extends JpaRepository<ArtifactEntity, Long> {

    Optional<ArtifactEntity> findByArtifactKey(String artifactKey);

    boolean existsByArtifactKey(String artifactKey);

    Optional<ArtifactEntity> findByContentHash(String contentHash);

    List<ArtifactEntity> findByExecutionKeyOrderByArtifactKey(String executionKey);

    List<ArtifactEntity> findByParentKeyOrderByArtifactKey(String parentKey);

    List<ArtifactEntity> findByExecutionKeyAndParentKeyOrderByArtifactKey(String executionKey, String parentKey);

    long countByParentKey(String parentKey);
}
```

---

# 4. Introduce a weak-ref proxy cache

The pattern is:

* keep strong identity metadata in a registry
* keep weak references to loaded node state
* when weak refs clear, clean cache metadata with a `ReferenceQueue`
* correctness never depends on the queue

## `ArtifactNodeState`

```java
package com.hayden.multiagentide.artifacts;

import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;

import java.util.List;

public record ArtifactNodeState(
        String executionKey,
        ArtifactKey artifactKey,
        String parentKey,
        Artifact artifact,
        List<String> childKeys
) {
}
```

## `ArtifactNodeWeakRef`

```java
package com.hayden.multiagentide.artifacts;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

final class ArtifactNodeWeakRef extends WeakReference<ArtifactNodeState> {

    private final String cacheKey;

    ArtifactNodeWeakRef(String cacheKey, ArtifactNodeState referent, ReferenceQueue<ArtifactNodeState> queue) {
        super(referent, queue);
        this.cacheKey = cacheKey;
    }

    String cacheKey() {
        return cacheKey;
    }
}
```

## `ArtifactNodeCache`

```java
package com.hayden.multiagentide.artifacts;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.lang.ref.ReferenceQueue;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ArtifactNodeCache {

    private final Map<String, ArtifactNodeWeakRef> cache = new ConcurrentHashMap<>();
    private final ReferenceQueue<ArtifactNodeState> queue = new ReferenceQueue<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private Thread reaperThread;

    @PostConstruct
    public void start() {
        reaperThread = Thread.ofVirtual().name("artifact-node-cache-reaper").start(() -> {
            while (running.get()) {
                try {
                    ArtifactNodeWeakRef ref = (ArtifactNodeWeakRef) queue.remove();
                    cache.remove(ref.cacheKey(), ref);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (reaperThread != null) {
            reaperThread.interrupt();
        }
    }

    public Optional<ArtifactNodeState> get(String executionKey, String artifactKey) {
        String key = cacheKey(executionKey, artifactKey);
        ArtifactNodeWeakRef ref = cache.get(key);
        if (ref == null) {
            return Optional.empty();
        }
        ArtifactNodeState state = ref.get();
        if (state == null) {
            cache.remove(key, ref);
            return Optional.empty();
        }
        return Optional.of(state);
    }

    public void put(ArtifactNodeState state) {
        String key = cacheKey(state.executionKey(), state.artifactKey().value());
        cache.put(key, new ArtifactNodeWeakRef(key, state, queue));
    }

    public void invalidate(String executionKey, String artifactKey) {
        cache.remove(cacheKey(executionKey, artifactKey));
    }

    public void invalidateExecution(String executionKey) {
        cache.keySet().removeIf(k -> k.startsWith(executionKey + "::"));
    }

    private String cacheKey(String executionKey, String artifactKey) {
        return executionKey + "::" + artifactKey;
    }
}
```

---

# 5. Create a topology-driven loader

This is the service that materializes a node from DB when the weak ref is gone.

## `ArtifactNodeLoader`

```java
package com.hayden.multiagentide.artifacts;

import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentide.artifacts.entity.ArtifactEntity;
import com.hayden.multiagentide.artifacts.repository.ArtifactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ArtifactNodeLoader {

    private final ArtifactRepository artifactRepository;
    private final ArtifactService artifactService;
    private final ArtifactNodeCache cache;

    public Optional<ArtifactNodeState> load(String executionKey, ArtifactKey artifactKey) {
        return cache.get(executionKey, artifactKey.value())
                .or(() -> loadFromDb(executionKey, artifactKey));
    }

    private Optional<ArtifactNodeState> loadFromDb(String executionKey, ArtifactKey artifactKey) {
        Optional<ArtifactEntity> entityOpt = artifactRepository.findByArtifactKey(artifactKey.value());
        if (entityOpt.isEmpty()) {
            return Optional.empty();
        }

        ArtifactEntity entity = entityOpt.get();
        if (!executionKey.equals(entity.getExecutionKey())) {
            return Optional.empty();
        }

        Artifact artifact = artifactService.deserializeArtifact(entity).orElse(null);
        if (artifact == null) {
            return Optional.empty();
        }

        List<String> childKeys = entity.getChildIds() != null
                ? List.copyOf(entity.getChildIds())
                : artifactRepository.findByExecutionKeyAndParentKeyOrderByArtifactKey(executionKey, artifactKey.value())
                        .stream()
                        .map(ArtifactEntity::getArtifactKey)
                        .toList();

        ArtifactNodeState state = new ArtifactNodeState(
                executionKey,
                artifactKey,
                entity.getParentKey(),
                artifact,
                childKeys
        );

        cache.put(state);
        return Optional.of(state);
    }

    public List<ArtifactNodeState> loadChildren(String executionKey, ArtifactKey parentKey) {
        return artifactRepository.findByExecutionKeyAndParentKeyOrderByArtifactKey(executionKey, parentKey.value())
                .stream()
                .map(entity -> load(executionKey, new ArtifactKey(entity.getArtifactKey())))
                .flatMap(Optional::stream)
                .toList();
    }
}
```

This is the layer that makes the weak-ref design usable.

---

# 6. Add a lazy proxy object

This is the object you pass around instead of keeping a full in-memory tree.

## `ArtifactNodeProxy`

```java
package com.hayden.multiagentide.artifacts;

import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

public class ArtifactNodeProxy {

    private final String executionKey;
    private final ArtifactKey artifactKey;
    private final ArtifactNodeLoader loader;

    public ArtifactNodeProxy(String executionKey, ArtifactKey artifactKey, ArtifactNodeLoader loader) {
        this.executionKey = executionKey;
        this.artifactKey = artifactKey;
        this.loader = loader;
    }

    public String executionKey() {
        return executionKey;
    }

    public ArtifactKey artifactKey() {
        return artifactKey;
    }

    public Optional<ArtifactNodeState> state() {
        return loader.load(executionKey, artifactKey);
    }

    public Artifact artifact() {
        return state().map(ArtifactNodeState::artifact)
                .orElseThrow(() -> new NoSuchElementException("Artifact not found: " + artifactKey.value()));
    }

    public List<ArtifactNodeProxy> children() {
        return loader.loadChildren(executionKey, artifactKey).stream()
                .map(child -> new ArtifactNodeProxy(executionKey, child.artifactKey(), loader))
                .toList();
    }

    public Optional<ArtifactNodeProxy> parent() {
        return artifactKey.parent()
                .map(parentKey -> new ArtifactNodeProxy(executionKey, parentKey, loader));
    }
}
```

---

# 7. Rebuild recursive tree views only on demand

When the user wants a full tree, project it from DB topology plus payload-only nodes.

## `ArtifactTreeProjector`

```java
package com.hayden.multiagentide.artifacts;

import com.hayden.acp_cdc_ai.acp.events.Artifact;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ArtifactTreeProjector {

    private final ArtifactNodeLoader loader;

    public Artifact projectTree(String executionKey, ArtifactNodeProxy root) {
        ArtifactNodeState state = root.state()
                .orElseThrow(() -> new IllegalStateException("Missing root state for " + root.artifactKey().value()));

        List<Artifact> children = root.children().stream()
                .map(child -> projectTree(executionKey, child))
                .toList();

        return state.artifact().withChildren(children);
    }
}
```

This replaces the old assumption that JSON already contained children.

---

# 8. Make `ArtifactTreeBuilder` incremental

Instead of holding a full in-memory trie, persist each node as it arrives and use the proxy loader for reads.

You can still keep a small map of execution roots, but not full trees.

## `ArtifactTreeBuilder` sketch

```java
package com.hayden.multiagentide.artifacts;

import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentide.artifacts.entity.ArtifactEntity;
import com.hayden.multiagentide.artifacts.repository.ArtifactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class ArtifactTreeBuilder {

    private final ArtifactRepository artifactRepository;
    private final ArtifactService artifactService;
    private final ArtifactNodeLoader artifactNodeLoader;
    private final ArtifactTreeProjector projector;
    private final ArtifactNodeCache cache;

    private final Map<String, ArtifactKey> executionRoots = new ConcurrentHashMap<>();

    public boolean addArtifact(Artifact artifact) {
        return getInsertionExecutionKey(artifact)
                .map(executionKey -> addArtifact(executionKey, artifact))
                .orElse(false);
    }

    @Transactional
    public boolean addArtifact(String executionKey, Artifact artifact) {
        ArtifactKey key = artifact.artifactKey();

        if (key == null || key.value() == null) {
            return false;
        }

        if (key.isRoot()) {
            executionRoots.putIfAbsent(executionKey, key);
        } else {
            ensureParentChain(executionKey, key.parent().orElse(null));
        }

        if (artifactRepository.existsByArtifactKey(key.value())) {
            return true; // idempotent for now
        }

        boolean saved = artifactService.persistDirectArtifact(executionKey, artifact);
        if (saved) {
            artifactNodeLoader.load(executionKey, key); // warm cache shallowly
            key.parent().ifPresent(parent -> refreshParentChildIds(executionKey, parent));
        }
        return saved;
    }

    private void ensureParentChain(String executionKey, ArtifactKey key) {
        if (key == null) {
            return;
        }
        if (artifactRepository.existsByArtifactKey(key.value())) {
            return;
        }

        key.parent().ifPresent(parent -> ensureParentChain(executionKey, parent));

        Artifact.IntermediateArtifact placeholder = Artifact.IntermediateArtifact.builder()
                .artifactKey(key)
                .metadata(new java.util.LinkedHashMap<>())
                .children(java.util.List.of())
                .expectedArtifactType(null)
                .hash(null)
                .build();

        artifactService.persistDirectArtifact(executionKey, placeholder);
        key.parent().ifPresent(parent -> refreshParentChildIds(executionKey, parent));
    }

    private void refreshParentChildIds(String executionKey, ArtifactKey parentKey) {
        artifactRepository.findByArtifactKey(parentKey.value()).ifPresent(parent -> {
            var childIds = artifactRepository.findByExecutionKeyAndParentKeyOrderByArtifactKey(executionKey, parentKey.value())
                    .stream()
                    .map(ArtifactEntity::getArtifactKey)
                    .toList();

            parent.setChildIds(new java.util.ArrayList<>(childIds));
            artifactRepository.save(parent);
            cache.invalidate(executionKey, parentKey.value());
        });
    }

    public Optional<ArtifactNodeProxy> getExecutionTree(String executionKey) {
        ArtifactKey rootKey = executionRoots.get(executionKey);
        if (rootKey == null) {
            rootKey = artifactRepository.findByExecutionKeyOrderByArtifactKey(executionKey).stream()
                    .filter(e -> e.getParentKey() == null)
                    .findFirst()
                    .map(e -> new ArtifactKey(e.getArtifactKey()))
                    .orElse(null);
            if (rootKey != null) {
                executionRoots.put(executionKey, rootKey);
            }
        }

        return Optional.ofNullable(rootKey)
                .map(key -> new ArtifactNodeProxy(executionKey, key, artifactNodeLoader));
    }

    public Optional<Artifact> buildArtifactTree(String executionKey) {
        return getExecutionTree(executionKey)
                .map(root -> projector.projectTree(executionKey, root));
    }

    public void clearExecution(String executionKey) {
        executionRoots.remove(executionKey);
        cache.invalidateExecution(executionKey);
    }

    private Optional<String> getInsertionExecutionKey(Artifact artifact) {
        return executionRoots.keySet().stream()
                .filter(s -> artifact.artifactKey().value().startsWith(s))
                .max(Comparator.comparing(String::length));
    }
}
```

---

# 9. Replace placeholders when the real artifact arrives

Right now `persistDirectArtifact(...)` is idempotent false-if-exists. For placeholders, you want:

* if existing row is `IntermediateArtifact`
* and new artifact has same key
* replace payload fields, preserve topology

## Add to `ArtifactService`

```java
@Transactional
public boolean persistOrReplacePlaceholder(String executionKey, Artifact artifact) {
    if (artifact == null || artifact.artifactKey() == null) {
        publishPersistenceError("Cannot persist null artifact directly", null, null);
        return false;
    }

    String artifactKey = artifact.artifactKey().value();

    try {
        Optional<ArtifactEntity> existingOpt = artifactRepository.findByArtifactKey(artifactKey);

        if (existingOpt.isEmpty()) {
            return toEntity(executionKey, artifact)
                    .flatMap(this::save)
                    .isPresent();
        }

        ArtifactEntity existing = existingOpt.get();
        Optional<Artifact> existingArtifactOpt = deserializeArtifact(existing);

        if (existingArtifactOpt.isPresent() && existingArtifactOpt.get() instanceof Artifact.IntermediateArtifact) {
            ArtifactEntity replacement = toEntity(executionKey, artifact)
                    .orElseThrow();

            replacement.setId(existing.getId());
            replacement.setCreatedDate(existing.getCreatedDate());
            replacement.setChildIds(existing.getChildIds()); // preserve topology
            replacement.setParentKey(existing.getParentKey());
            replacement.setExecutionKey(existing.getExecutionKey());

            artifactRepository.save(replacement);
            return true;
        }

        // idempotent same-key same-hash case
        if (existing.getContentHash() != null && existing.getContentHash().equals(artifact.contentHash().orElse(null))) {
            return true;
        }

        // otherwise same-key different payload = conflict in this version
        return false;
    } catch (Exception e) {
        publishPersistenceError("Failed to persist artifact directly " + artifactKey, artifact.artifactKey(), e);
        return false;
    }
}
```

Then `ArtifactTreeBuilder.addArtifact(...)` should call `persistOrReplacePlaceholder(...)` instead of `persistDirectArtifact(...)`.

---

# 10. Important behavior change for `deserializeArtifact`

This is the exact rule now:

## Deserialization no longer reconstructs children

It reconstructs only the payload object at that key.

If you want a recursive artifact tree, you must call the projector:

```java
Artifact fullTree = projector.projectTree(executionKey, rootProxy);
```

That is the correct place to attach children.

---

# 11. Why this fixes the bug

Before:

* parent JSON contained serialized children
* child also existed as its own DB row
* updates could diverge
* loading a ref/payload could accidentally pull stale or foreign subtree state

After:

* JSON contains only payload/body for that occurrence
* children are attached only from relational topology
* `ArtifactDbRef` still works
* children on a ref occurrence remain occurrence-local
* no ancestor JSON rewrite is needed when a child changes

---

# 12. The minimal rule set this implementation assumes

This version assumes:

* `contentJson` is payload-only
* `parentKey` and DB child lookup define topology
* `ArtifactDbRef` shares payload, not subtree
* placeholder replacement at same key is allowed
* same-key different concrete payload is a conflict
* weak refs are cache-only, not correctness-critical

---

# 13. One thing I would strongly recommend next

Your `Artifact` model still exposes `children()` everywhere, which is fine for a domain view, but it means you must remember:

* persisted artifact JSON is not the full domain tree
* projected artifact tree is a reconstructed view

That distinction should be documented clearly, because it becomes a core invariant.

If you want, I can next turn this into a tighter patch against your exact existing classes, including the exact methods
in `ArtifactTreeBuilder` and `ArtifactService` that should be replaced.

```

So I'm thinking that we start with this, but instead make the field for artifact a json object, and more importantly, 
we have ArtifactNodeView as the field, with jdbc type json, without children, but we set a json ignore field for 
childKeys from artifact entity, to make sure we're not leaky as well. 