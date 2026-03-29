Instead of waiting for the tests to complete, we do the following:

1. commit, and push under a feature branch
2. push to github and do the runner (tests.sh) 
3. run the full test suite from that feature branch
4. once it's finished, we ask the model to review the outputs (the markdown files, for our new data intelligence testing).
5. based on what it finds, it pushes up specific changes to that one, and it may actually cancel runs to pull in that change for the next phase before it runs those tests 

so for this we use github mcp server and runners.
