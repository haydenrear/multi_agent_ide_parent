Add to our skill for data intelligence testing:

1. review the surface and see if we need to add anything
2. based on our surface and any updates, review our invariants, and see if we need to 
  - break down any of our invariants further along the axis of a previous invariant
  - introduce new invariant entirely
  - relax an invariant
3. review our data collection currently to see if it's sufficient
4. run our tests
5. review the data, testing each and every invariant, to make sure we're still correct
6. review our exploratory entrypoints, for each one of those, to our exploratory -> find any issues, if necessary this may lead back to 1.

Probably we should have a whole separate skill for testing, so we can just reference that skill.md. And then reference that from our agents.md and claude.md.


- We need to add prompt contributor stuff, prompt context stuff, that slipped through last time, which request decorators, result decorators, etc. ran.
