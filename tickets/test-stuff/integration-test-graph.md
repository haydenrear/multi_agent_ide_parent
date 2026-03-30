We need the ability to run the various reroute logic consistently, iterate through all of these cases to make sure that
they are valid, but able to do it in a way such that the AI can evolve these tests easily.

Here are some ways we did it:

1. test graph - however test graph is really only for UI and microservices 
2. junit - however junit integration tests have been challenging for the AI to debug, requiring the user to debug them

So the question is how can the cost be minimized for testing but able to evolve the test cases easily?

Probably we'll just use the Junit for now, find a way for the AI to more easily evolve it – a good way may be to 
write the blackboard history and context change to a log file. So then we won't have to pay to run initial tests, and
the AI can see better to see why they failed, without having to look through the entire log. Moreover, this log file
can be translated to when we run it with the AI. So the first ticket is adding our tracing dependency with the 
information we want to get out. We'll want to tag it at levels of granularity. So this is something we're already going
to be doing to create the dataset and then we'll add to the skill for running these tests the tags.

Then, there should be probably be another set of tests for running with the AI specifically, which include all the
cases, such as interrupts (routing to the various agents), context manager, agent topology, calling controller,
we'll create a list of features that we'll have prompts and tests for, and a prompt for proving that the AI produced 
them. It's probably a good idea for the AI to attach the trace to prove. It'll be a test matrix with proof – so this 
is where the test graph should be helpful because it provides a mechanism for a java program to run pointing at the 
program and run various validation tests. However, I'd like to start by using python scripts that capture the reports.
The cucumber reports will come in handy evenutally. However, during iteration for this, we can start with python scripts that
capture the trace information, it'll be a good idea to have infrastructure and snippets for this because the same data
will be searched through in the jupyter.

I think that the python scripts can be smart as well – they can do what the test graph did - which is, poll a desired 
state and then once we find that state transition, produce an effect to then change the state of the program:

- wait until reach discovery agent
- send a message to that discovery agent to route to ...
- wait until in interrupt ...

etc - and we can make this sort of reusable and everything, and then the controller running the tests acts as a poller,
and then we've got them written down somewhere, so they can just run, and then once we've got the patterns down, we can
capture them, clone it over to test graph, perhaps using mountebank for when it solidifies.

---

I've also been thinking of adding to this a jbang interface for test graph so that we can iterate more quickly.

Starting out with pulling out a library that provides java sources as a graph, being able to add them in the executables
the reason being that it should be able to load the exact source code it's testing, for example to save to database, 
for test prep, calling it easily, etc. Having it in Java provides a mechanism for the code to not have to be rewritten,
for the code that is in Java.

So in this case, it's like using gradle to run jbang, python nodes - exposing metadata, AI running them, gherkin reports
to view.