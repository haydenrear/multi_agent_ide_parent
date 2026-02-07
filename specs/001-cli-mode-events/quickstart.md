# Quickstart: CLI mode interactive event rendering

## Prerequisites

- Java 21
- From the Java parent module: `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent`

## Run in CLI Mode

1. Start the application with the CLI profile enabled:

```bash
cd /Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent
./gradlew :multi_agent_ide:bootRun --args='--spring.profiles.active=cli'
```

Optional environment overrides:

```bash
export CLI_REPOSITORY_URL=/path/to/target/repo
export CLI_BASE_BRANCH=main
export CLI_TITLE="CLI Goal"
```

2. When prompted, enter a goal.
3. Respond to any interrupt prompts to continue execution.
4. Observe event output in the terminal until the goal completes.

## Exit

- Use `Ctrl+C` to stop the CLI session once complete or if you need to abort.
