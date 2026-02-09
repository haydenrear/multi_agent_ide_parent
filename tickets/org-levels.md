Levels of organization are needed - for instance for the actual process of prompt versioning.

So for this there exists the infrastructure

# Process Manager

Generic process manager that allows for the following:

1. agents listening to particular ACP processes
2. writing to particular ACP processes
3. access to a composite blackboard through tooling

# Org-Level Abstraction

There can be any number of levels added - so the architecture of this can be explained simply

- listener -> aggregators
- aggregators -> manager 
- manager -> listener (repeated as many times for size of data)

