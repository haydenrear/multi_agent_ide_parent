Instead of waiting for the tests to complete, we do the following:

1. commit, and push under a temp branch
2. clone and pull that branch to a separate, tmp repo
3. run the full test suite from that tmp repo, then move forward with the next phase

So then as we move from phase to phase, we'll go back to the previous branch, merging into main once that one's test is passing, creating the branch in tmp, running it.

We could do it as a github runner with pull requests as well, but they usually stop the currently running one, and cost money. So we just encapsulate it.
