- Add the action name and name in prompt context, and anything else to pass to decorators and decorator context
- Add another class for calling LLM, put decorator calls in that, and put LlmRunner in that

To make it so that all llm calls automatically go through the decorators