
So we have our DegenerateLoopDetector. And this can be extracted into a single RoutingPolicy. Then, we can add more
of these RoutingPolicy-s. We look in the blackboard, and this helps translate into a PlanAdvice from the RoutingPolicy. 

So this will help inject information into the planner, and more clearly, will give us some information to match over
for the next Plan.

We can then match over them to augment the prompt. For instance, in the PromptContext, then we have the PlanAbstraction.
This helps us cancel out some terms in the prompts, and create a comprehensive algebra for our dynamic prompts.

