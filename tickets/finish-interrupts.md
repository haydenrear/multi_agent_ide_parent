After the interrupts, the interrupt result should be translated into it's output and appended to the request to be routed back
using an LLM. So each interrupt needs after it's result, an LLM call for routing.

Additionally, the interrupt requests need to be more specific, more typed. In particular, for "human review", the AI
should ask questions and clarifications, and validate implementation details. This is really important - and it's the
innovation from the interrupts, because currently you have to approve code, and at a high level you approve something,
but often the AI fails to translate requirements successfully. The human review then should transition often for 
some implementation details, and should even provide potential answers, A., B., or C., or write-in, etc. So by extracting
this out into the controller, the controller, which starts as a human, can be better. Additionally, the human will then
be able to create research tasks to answer the model.

Because remember that the entire conversation is supposed to be to collector of orchestrator, then it's over, the back
and forth that currently exists is this interrupt. And typing out and structuring the interrupts to extract the 
controller is a valuable dataset to be extracted.