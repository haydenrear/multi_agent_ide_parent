
For particularly the controller model.

The controller model should be able to set collector over the events. It collects some k events of type t, or where match,
where k is described by a number and a duration, then, for those k events, if there are any k, a collector operator is
provided and a function is called for summary.

So this makes it so that we don't use a lot of excess tokens, and we automatically summarize some events easily instead
of having to read all of them.