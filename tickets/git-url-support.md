Currently we only support local file paths, not git remotes.

So we get this error:

```log
pting to start orchestrator - One of setGitDir or setWorkTree must be called..
java.lang.IllegalArgumentException: One of setGitDir or setWorkTree must be called.
	at org.eclipse.jgit.lib.BaseRepositoryBuilder.requireGitDirOrWorkTree(BaseRepositoryBuilder.java:638)
	at org.eclipse.jgit.lib.BaseRepositoryBuilder.setup(BaseRepositoryBuilder.java:602)
	at org.eclipse.jgit.storage.file.FileRepositoryBuilder.build(FileRepositoryBuilder.java:55)
	at com.hayden.utilitymodule.git.RepoUtil.findRepo(RepoUtil.java:396)
	at com.hayden.multiagentide.service.GitWorktreeService.cloneRepository(GitWorktreeService.java:1332)
	at com.hayden.multiagentide.service.GitWorktreeService.createMainWorktree(GitWorktreeService.java:82)
	at com.hayden.multiagentide.agent.AgentLifecycleHandler.initializeOrchestrator(AgentLifecycleHandler.java:116)
	at com.hayden.multiagentide.controller.GoalExecutor.executeGoal(GoalExecutor.java:40)
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)
	at java.base/java.lang.reflect.Method.invoke(Method.java:580)
	at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:359)
	at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)
	at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)
	at org.springframework.aop.interceptor.AsyncExecutionInterceptor.lambda$invoke$0(AsyncExecutionInterceptor.java:114)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:317)
	at java.base/java.lang.VirtualThread.run(VirtualThread.java:311)
18:24:28.396 [task-1] INFO  c.h.m.a.ArtifactEventListener - Found event NodeErrorEvent.
```
