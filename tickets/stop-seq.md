There should be two options for stopping

1. stop sequence - this stops an agent process in a dirty way, simply calls stop() on an agent process
2. stop with summary - this intercepts the agent and based on where it is in the graph, injects a prompt requesting for it to be summarized and finished. 

So currently any agent can receive a message, and the message goes to the most recent agent who's working. However, stopping therefore has an issue.

So what we should do then on receiving a stop sequence