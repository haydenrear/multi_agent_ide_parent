# Many ticket MCP for LSP ... what a headache ... the time it takes to index, etc.

Worktrees should be under parent name of repo - then there should be "spots" - and the reason is that any arbitrary
MCP server will remember stuff about that spot, even if it's deleted. So then if there's none available, another one
is created - but if there's one there, then we use previous metadata about that slot
