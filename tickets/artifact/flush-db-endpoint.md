There should be a flush-artifacts endpoint - we POST it with an artifact ID, and it flushes the artifacts to the 
database. This will be for when stuff fails but we want to keep the data.

We'll probably just want to flush to the database every time we get a message...