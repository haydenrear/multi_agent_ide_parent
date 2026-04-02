Currently it's all on "main".

However, this is simplified and will be replaced with git flow.

The AI needs to be prompted to, when it runs clone_pull.py, use a feature branch associated with tags, not just main.

Then worktree gets merged into that feature branch, then that feature branch gets PR.
