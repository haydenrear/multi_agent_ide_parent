Need to emit a 

PromptCheckBreakdownEvent which will be a Events.GraphEvent 

it (non-ai event) breaks down the prompt into sections, with statistics on those sections - can be viewed efficiently by the AI.

Then we'll add a script for breaking further searching in those sections to search through the prompt.

The idea being that we'll need to be able to easily debug the prompt in an effective way. And being able to decompose the prompt (per-prompt decomposition) will be the best.

Then each one will have also promptText.

So each PromptContributor has 

PromptComponents - recursive data structure each PromptComponent has further key and value.

So then the controller AI can write a script to search through the prompts to find waste.
