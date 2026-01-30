
```java
/**
 * Maybe there exists mix-ins for history entries, describing actions to be executed with tools.
 *  - The first of which is the paged entry, and the action provided is paging
 *    through these messages.
 *  - The second is the default entry which is just printed, and then this print is paged according to token size
 *  - The third is lazy retrieval of fields in an entry - this is for records that contain many contexts, because those contexts
 *    will be very heavy and may be context-killers
 */
public interface HistoryEntryValueMixIn {

    interface PagedHistoryEntryValueMixIn {
    }

    interface LazyContextHistoryEntryValueMixIn {
    }

}
```