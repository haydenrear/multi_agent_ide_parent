
For the sandbox strategy for AcpTooling, one way to do it for read and write for some files would be to run the process
as a particular user, and before running just provide the file permissions to that user. So then if it fails, then 
ask for permission. 

We can use this for the bash - because then we catch the error - try to escalate permission, and run again, and if it
fails again, then we know.