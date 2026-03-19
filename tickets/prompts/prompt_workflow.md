Propagator -> notify if prompts are bad
Transformer/Filter -> Test out new prompts in real time ??

```markdown
I want you to run it all the way through, monitoring the progress, and advising the agents if you see them operate out of bounds. You'll check the propagators for the request, results, and for the prompts as well. Also, I'd like you to, if there are code changes, validate that they happen to the correct repository, and validate that the changes are merged into the correct worktrees. 

Planning and discovery should not be writing anything to the repositories, we just provide them the worktree for sandbox. We'll want to see valuable discovery and solid planning, and we'll want to see the tickets being completed and merged in correctly, and then the ticket collector properly reviewing them and deciding what to do. Additionally, I'd like you to review the changes as they're happening and add messages if you think we're getting off track. Make sure when you do this to include a disclaimer to still use the structured responses when it returns. 

Please review the [@SKILL_PARENT.md](file:///Users/hayde/IdeaProjects/multi_agent_ide_parent/skills/multi_agent_ide_skills/SKILL_PARENT.md) and consider the following 

- we'll be using [@standard_workflow.md](file:///Users/hayde/IdeaProjects/multi_agent_ide_parent/skills/multi_agent_ide_skills/multi_agent_ide_controller/workflows/standard_workflow.md) , but review the associated SKILL.md - because we may want to improve it, and feel free to make suggestions.
- make sure to review, use, improve and add to executables in the skills/multi_agent_ide_skills/multi_agent_ide_controller/executables for when we're doing anything from polling to registering. It's a good way to pass documentation to the next person on the job.
- similarly, when we're searching through the logs to identify bugs, review, use, improve and add to executables in the skills/multi_agent_ide_skills/multi_agent_ide_debug/executables - they'll be python scripts for quickly parsing the log to search for particular things
- when you do use, review, update executables, make sure to use the associated reference.md
- if you identify any issues, as I'm sure you will, along the path, we may want to continue with the process. Add any outstanding bugs or issues to [@outstanding.md](file:///Users/hayde/IdeaProjects/multi_agent_ide_parent/skills/multi_agent_ide_skills/multi_agent_ide_debug/references/outstanding.md) , and we'll come back to it - this even includes when the agent is getting off-track and you have to send them a message
- when you send an agent a message, make sure to send it to the agent's node ID, which is it's chat session key. If you send it to the base, for instance, you'll send it to the orchestrator - not the discovery agent, or whichever you're targeting
- if you see an issue and there's a need to change a prompt, then you can add a transformer or a filter to quickly update a prompt contributor, or the prompt itself. This comes in handy if you see a bug, or if your propagator shows an error or an improvement and you want to test it in that particular session.
```