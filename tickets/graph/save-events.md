The saving of the graph nodes is a bit decentralized and hard to reason about. However, all of these have events,
which are being saved in SaveEventToEventRepositoryListener. Therefore, some of these nodes can be saved in a 
listener like this, by listening to AddNodeEvent, ChangeNodeStatusEvent, AddChildNodeEvent, etc.

It's easiest if we sort the listeners and then have one dedicated to managing the graph to keep everything standardized
as more of a cross-cutting concern. Then, in the events, with JsonIgnore, we add the actual event as a field (more of 
a ref, and then the first event listener saves it or updates the existing.) This is a good way to synchronize the 
behavior - if everything goes through there and is based on events happening it's more easy to reason about.

