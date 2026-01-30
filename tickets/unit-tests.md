
Many of the unit tests in multiagentide are not testing hardly anything about the workflow. 
They aren't testing the creation of git repo, they aren't testing merging of git repo, they aren't testing, either 
of these in submodule case - and many of them are just validating one or two events. 

These tests need to be added to so that we're making sure to test all of those things, and then additionally, we need
to add the add message, interrupt, prune, add branch.