# Blackboard History Tools Contracts

This document defines the logical API contracts for the Blackboard History tools. These tools are used by the Context
Manager to inspect and curate workflow history.

## 1. History Trace Tool

**Goal**: Retrieve ordered history for specific actions or agents.

### Request: `HistoryTraceRequest`

| Field              | Type              | Description                    |
|--------------------|-------------------|--------------------------------|
| `actionNameFilter` | String (Optional) | Filter entries by action name. |
| `agentNameFilter`  | String (Optional) | Filter entries by agent name.  |

### Response: `HistoryTraceResponse`

| Field     | Type                            | Description                                     |
|-----------|---------------------------------|-------------------------------------------------|
| `entries` | `List<BlackboardHistory.Entry>` | Chronological list of matching history entries. |

---

## 2. History Listing Tool

**Goal**: Iterate through history with pagination and time bounds.

### Request: `HistoryListingRequest`

| Field       | Type               | Description                          |
|-------------|--------------------|--------------------------------------|
| `offset`    | Integer            | Starting index (0-based).            |
| `limit`     | Integer            | Maximum number of entries to return. |
| `startTime` | Instant (Optional) | Filter entries after this time.      |
| `endTime`   | Instant (Optional) | Filter entries before this time.     |

### Response: `HistoryListingResponse`

| Field        | Type                            | Description                                 |
|--------------|---------------------------------|---------------------------------------------|
| `entries`    | `List<BlackboardHistory.Entry>` | Page of history entries.                    |
| `hasMore`    | Boolean                         | True if more entries exist after this page. |
| `nextOffset` | Integer                         | The offset to use for the next page.        |

---

## 3. History Search Tool

**Goal**: Find specific information using text search.

### Request: `HistorySearchRequest`

| Field        | Type    | Description                                            |
|--------------|---------|--------------------------------------------------------|
| `query`      | String  | Text string to search for within entry content/inputs. |
| `maxResults` | Integer | Maximum number of matches to return.                   |

### Response: `HistorySearchResponse`

| Field     | Type                            | Description                        |
|-----------|---------------------------------|------------------------------------|
| `entries` | `List<BlackboardHistory.Entry>` | Entries matching the search query. |

---

## 4. History Item Retrieval Tool

**Goal**: Fetch a specific entry by reference.

### Request: `HistoryItemRequest`

| Field     | Type               | Description                                |
|-----------|--------------------|--------------------------------------------|
| `index`   | Integer (Optional) | The 0-based index of the entry.            |
| `entryId` | String (Optional)  | The unique ID of the entry (if available). |

### Response: `HistoryItemResponse`

| Field   | Type                      | Description                                      |
|---------|---------------------------|--------------------------------------------------|
| `entry` | `BlackboardHistory.Entry` | The requested entry, or null/error if not found. |

---

## 5. Context Snapshot Creation Tool

**Goal**: Create a curated bundle of history for recovery.

### Request: `CreateSnapshotRequest`

| Field          | Type            | Description                                 |
|----------------|-----------------|---------------------------------------------|
| `entryIndices` | `List<Integer>` | Indices of history entries to include.      |
| `summary`      | String          | Human-readable summary of the context.      |
| `reasoning`    | String          | Explanation of why this context was chosen. |

### Response: `CreateSnapshotResponse`

| Field      | Type              | Description                                                     |
|------------|-------------------|-----------------------------------------------------------------|
| `snapshot` | `ContextSnapshot` | The created snapshot object containing references and metadata. |

---

## 6. Blackboard Note Tool

**Goal**: Annotate history entries with reasoning.

### Request: `AddNoteRequest`

| Field          | Type            | Description                                     |
|----------------|-----------------|-------------------------------------------------|
| `entryIndices` | `List<Integer>` | Indices of history entries to annotate.         |
| `content`      | String          | The note text.                                  |
| `tags`         | `List<String>`  | Classification tags (e.g., "error", "routing"). |

### Response: `AddNoteResponse`

| Field  | Type          | Description       |
|--------|---------------|-------------------|
| `note` | `HistoryNote` | The created note. |