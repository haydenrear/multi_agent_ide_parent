
When an artifact is received and added to artifact-tree builder, to support restarts and fault-tolerance, and failures, because we only write ArtifactEntity to the database at the end of the process, and we'd like to support restart.

So additionally there should be a check to see if the goal is completed, and then flush directly.

And there needs to be synchronization added as well there for if/when concurrent for a node ID (right now it's single
process, single process single thread, no worries).