Add to the deploy script to:

1. do the deployment as a docker run
2. mount the server port and return that
3. mount a directory as the worktree directory so the controller can merge that 

So that the controller can deploy/test multiple changes at a time.

depends on: goal cloning from git URL.