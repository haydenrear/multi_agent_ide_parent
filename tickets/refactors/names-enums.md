There are a lot of names and constants

In BlackboardHistory, etc.

So what I'd like to do is

create a systematic way to document information about 

actions
names
loop actions to ignore
...etc...

I'd like to make them enums, but I'd like to for instance put them in the parent
of the package, with inner classes.

And then have sort of a central location where they're saved in maps, sort of idea.

Because often we can mix up strings, but if we use enums, and have central locations, we don't rewrite, and then we have a way to abstract over the program
