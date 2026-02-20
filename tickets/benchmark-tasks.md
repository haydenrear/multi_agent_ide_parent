In the test supervisor for testing changes to the prompts during development, you have some arbtirary number of tasks, performing them in order of difficulty and context length. And when a prompt evolution is completed, the tasks run in order, and the LLM polls them, and decides how they progressed, and whether the prompt evolution works.

So this basically runs in a loop, as long as it can, with the steering LLM making prompt changes, running the benchmarks, analyzing the result, and repeating in a self-improvement loop.
