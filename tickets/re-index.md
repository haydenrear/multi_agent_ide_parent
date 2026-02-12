The cdc code-search second step could use Intellij community.

However, more interestingly still is to, as we embed the repository, input it into a small model and ask the model to produce a JSON output as well as the embedding, or as an intermediary step, produce the JSON output, then embed that.

So we can index a repository, then show it the ast with all of the FQDN, then ask the model to produce the JSON output that is the AST with all refs resolved. We can do this with a smaller model, make it agentic, extract it into a very small agentic model that trails the user. So we then can use the indexes we produce determinstically to train a retrieval model and a small agentic model that resolves references and indexes on demand. So interestingly, it may even be faster than searching through all libs, for instance.

So we currently have ability to create some indexes in Java - and we can then take these and use it to produce not only retrieval but also arbitrary reference calls. We'll basically produce a chain of tool calls, for each find all references, it's basically infinite data...

1. if libs, go to libs directory, if code, go to code directory, or rg for source code file
2. translate into FQDN 

For lambdas, mostly it will be contextual, but there can be cases resolved for this also, and a workflow with maybe 2-3 calls. So this in fact skips all indexing and saving of indexes... replacing only with code files and extraction, in a standardized way.
