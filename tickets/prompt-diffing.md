
The ACP session manages the history in most cases, doing context compaction automatically.

However, because of this, if we send the prompt over and over again, it leads to unnecessary tokens.

So it should be analyzed which prompts should continue to be sent, and which should not be. Also we have cached 
and so the caching may make it very cheap.


I'm noticing that I'm sending the same prompts to an open session, in the [@multi_agent_ide](file:///Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide) . If the session has already been created, however, we should instead use a follow prompt. All agents recycle sessions, and skip context management, leaving it to the chat model, except for the dispatched agents.

So in this case, we have our [@PromptContributorFactory.java](file:///Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/prompt/PromptContributorFactory.java) in [@prompt](file:///Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/prompt) and [@prompts](file:///Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/resources/prompts) and in [@workflow](file:///Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/resources/prompts/workflow) we have some more prompts.