Run the utility/routing planner for performance - can utility do it?

A routing planner would be the simplest planner. It uses a hashmap underlying for constant lookup and if using routing planner each action can only have one non-context arg.

So by limiting to one arg per then it's not combinatorial and you just route to the non-null arg added most recently.