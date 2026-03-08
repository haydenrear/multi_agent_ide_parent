For starters:

1. dispatched agents don't even HAVE a stuck handler !!!
2. the blackboard history ... handling in our own special planner, means we don't need to hide anymore - which will help
   quite a bit (and at some case we may try some of these other ones).
3. our own special planner, means we can handle errors in our own specific way.

So for rememberance

for starters, we can, if there is nobody in the blackboard, check the blackboard history

```java
        var lastResult = bws.getBlackboard().lastResult();
        if (lastResult == null) {
//            
            log.debug("No last result on blackboard, no action can be selected");
            return null;
        }

```

Here we can check if there exists a blackboard history, and then take the last result off of there - or we can just stop
hiding in RegisterAndHideInputRequestDecorator and RegisterAndHideInputResultDecorator in 
BlackboardHistoryService.register.

But more importantly, can't there be smarter ways to handle this routing per error.

How can we define those?

dispatched agent fails ->
dispatch agent fails ->
fails during merge, fails during commit, fails due to this output reason, etc.

And in particular, I think, we'll want to make this in some sense dynamic for the controller - why not? 

Let's say we have a process running, our controller is watching, we see it fails in a particular way - we can 
register a mechanism for routing on the failure so it can inspect the context, match over it, and handle it.

And then we have, back to the beginning...

an algebra over the blackboard history creating the next step of the plan (sort of a utility planner with a bit of 
dynamic).

And one important one is request more information from the controller, request more information from context manager. 

And then options as to what it will do, such as, retry request with this verbage now... etc.

And this one is really the one we want the most, to help.