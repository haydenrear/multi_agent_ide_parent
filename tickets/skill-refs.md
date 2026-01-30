One thing that can be helpful is a retrieval model for skills.

So we have a PromptContributor that contains the xml for all skills available for a particular agent, name and description. This goes to all agents, and we have augmentations on it for particular agents. The template text gets versioned for specific agents, or particular skills groups.

Then, the structured response contains a list of skills that were loaded by the model. So then we save that information into the database, and we can, in the future, add infinite skills and rank them using our model, retrieve them based on conversation history, etc.