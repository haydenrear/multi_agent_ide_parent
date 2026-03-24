In the planner, there is the case where the return value is null, a bad state.

What we can do is call the controller and ask it to search through its history and produce the next and provide it
something like the filtered context manager routing schema.

So the permission gate requests that the controller search through the events and specify where to route to next, 
providing the schema and an endpoint, and waits for the response - the wait event that is emitted can contain the 
schema. 

Then, on that endpoint it tries to deserialize the request from a string (because if you provide the entire thing, it
means it will have to add the schema twice to the context), failing and returning the error. It can deserialize the 
entire context manager request, and the response for validation if it fails will look for the event and return in the
error the schema again, along with the object mapper exception message. 