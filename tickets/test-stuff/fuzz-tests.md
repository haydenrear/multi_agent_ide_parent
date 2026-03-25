To test the behaviors quickly, we can produce the following, called fuzz tests:

1. define all contracts, on things like persistence, service, controller, and boundaries between them
2. produce fuzz tests to test all valid inputs for the contracts

So first we define the contract boundaries, then we define valid inputs. 

So for the database, for instance, we test persistence of the inputs.
For the service, we test pass-throughs.

Then we have full integration.

So these fuzz-tests can be produced quickly and they'll catch the things like null pointer exceptions, etc.
