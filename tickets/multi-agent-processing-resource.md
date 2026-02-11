

The reducer should definitely be on a different thread than the TUI. Also - there should be a metered update time
for the reducer. Additionally, sessions that are not "active" - can be not reduced. 

This brings an interesting question - about whether or not the amount of data becomes a problem in RAM? Probably not
... because 4gb is an insane amount of text, and a small amount of RAM