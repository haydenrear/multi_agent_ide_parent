# Artifact Key Hierarchy

When we call it with some key below a given key, then it should return all the nodes below that. It should first find the execution node from the key, then build the graph, then prune all the nodes that are higher then the key passed.

# Caching Result / Database 

Probably some of the counts can be cached or something ??? And a timing filter can be added to the queries? This will be saved in the graph database probably.
