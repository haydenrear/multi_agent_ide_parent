In the test supervisor for testing changes to the prompts during development, you have some arbtirary number of tasks, performing them in order of difficulty and context length. And when a prompt evolution is completed, the tasks run in order, and the LLM polls them, and decides how they progressed, and whether the prompt evolution works.

So this basically runs in a loop, as long as it can, with the steering LLM making prompt changes, running the benchmarks, analyzing the result, and repeating in a self-improvement loop.

---

This should be generated as a separate skill, and should include information about running all of the tests as well. It should say something like, this skill is to be used in conjunction with the multi_agent_test_supervisor skill and defines the order of operations when testing a prompt change. It's the loop, including 

- running unit tests
- running integration test profiles
- test graph (once we finish the node frontend)
- running multi_agent_test_supervisor with increasingly difficult software engineering tasks

And then how to validate the benchmark results.

In particular, we'll also want to add running the supervisor with different profiles, and specific information on when it failed for specific profiles and models, and whether to continue if it failed.

For instance, codex with ollama/openrouter with various models. We may want to run in a loop to test prompt changes to get things working with open source model, or testing with open source model, more as a scanner of all of the free open source models.
