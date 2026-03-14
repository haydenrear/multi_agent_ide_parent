A good way to do the transition is using a proxy-like structure, whereby we hold a weak reference. 

So we always save to start, making sure all parents are saved. Then we hold a ref, to a weak reference, which can be
cleared by the garbage collector. Then, if it gets cleared, we reload from database.

And then if the weak reg gets purged, and all parents get purged, then, as we parse the thing, we load the children,
so all children get dropped with the parent.

So I think this has to happen with a watcher on a weak reference queue. In other words, when something gets purged, then
it gets added to weak ref queue. Then we check in weak ref queue and do that cleanup. 

Then, because we parse the tree parent to child, they get loaded parent to child.  