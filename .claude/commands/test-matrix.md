So now we'll need to go ahead and run our @multi_agent_ide_java_parent/test.sh.

## Testing matrix

Run tests before deploying code changes:

| Test suite | Approximate duration | Bash timeout |
|------------|---------------------|--------------|
| Unit tests (`./gradlew :multi_agent_ide_java_parent:multi_agent_ide:test`) | ~3 minutes | 180000ms |
| Spring integration (`./gradlew :multi_agent_ide_java_parent:multi_agent_ide:test -Pprofile=integration`) | ~5-10 minutes | 600000ms |
| Full pipeline (`multi_agent_ide_java_parent/tests.sh`) | ~25 minutes | 900000ms |
| ACP integration (`./gradlew :multi_agent_ide_java_parent:multi_agent_ide:test -Pprofile=acp-integration`) | ~60 minutes | 3600000ms |
| ACP chat model (`./gradlew :multi_agent_ide_java_parent:acp-cdc-ai:test -Pprofile=acp-integration`) | ~3 minutes | 3600000ms |

- ACP chat model tests matter only when working on base-level ACP/Claude Code or Codex.
- Skip tests that don't cover your change surface.
- For `multi_agent_ide` integration tests: must use `-Pprofile=integration`, otherwise `**/integration/**` is excluded.
- For ACP chat model tests in `acp-cdc-ai`: must use `-Pprofile=acp-integration`.
- Do not run in parallel sub-agents, with async tasks, or as background tasks - poll manually every 5-10 mins when running long-running tests.

Before you run the tests, I'd like you to look through the changes we've made. We're using our INVARIANTS.md, SURFACE.md, and EXPLORATION.md to guide our test analysis and testing. We've written logs for our integration profile into multi_agent_ide_java_parent/multi_agent_ide/test_work. So first I'd like you to check our tests, our invariants, surface, and exploration and make sure we're logging enough data so that we'll be able to perform the analysis.

Then we'll go ahead and run the tests.

After that we'll want to:

1. check to see if our code changes introduced any changes to the SURFACE.md - you'll want to check the SURFACE.md, do a bit of analysis to see when it last changed, and then add any additional test surface. Really this is the high level behaviors that will be used. Make sure to include our skills/multi_agent_ide_skills in this analysis.
2. For each item that changed in SURFACE.md, update INVARIANTS.md and EXPLORATION.md. If you find something that is already existing in INVARIANTS.md, but needs additional care, then expand into another test dimension. We're thinking of this as a test tensor. We have our SURFACE.md which is our behaviors of our program, then we continuously break those down into more and more test dimensions.
3. For each of those items, check to see if we want to insert any exploration points in for our data. We use exploration as points that we check in our markdown dump, to check to see "what we don't know we don't know". Based on this, you may go back to step 1.
4. Then analyze the three files. Go to our markdown files and our logs. Check to see if our surface and invariants are being held. Then, for each of our exploration points, check to see if there's anything wrong.
5. Produce a report and we'll reconvene with your findings.
