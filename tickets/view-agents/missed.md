It's actually better to have the render script write the mental-models.md markdown, rendered with the stale/valid.

Because all add section, remove section, add ref can be added as scripts. Then there is no markdown file, it's all the metadata.

And the skill will say, never edit the mental-models markdown file, it will just write it to the current directory. And then the python script will set the file permissions to be read only (then change it to add write, write it, update it to be read only again).

Then in the rendered, there can be a last-updated. And then it won't have to pull in the context twice.

This also greatly simplifies the paths because it becomes a json path expression, and de-duplication becomes easier.

Then if we write, add a ref, update a section, it rewrites the markdown file as well. The markdown file, again, has a last updated section in the top. And the tool call to provide the file references for a section remains a tool call (no reason to add that) - only the sections say invalidated (an index into them).
