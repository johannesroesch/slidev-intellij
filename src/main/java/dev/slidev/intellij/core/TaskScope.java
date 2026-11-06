package dev.slidev.intellij.core;

import java.util.Set;
import java.util.concurrent.*;

/** Plugin-owned workers. Closing interrupts running work and forgets queued callbacks. */
public final class TaskScope implements Executor, AutoCloseable {
    private final Set<Future<?>> tasks=ConcurrentHashMap.newKeySet();
    private final ExecutorService workers=Executors.newCachedThreadPool(action -> {
        // Neither the context class loader nor inherited thread locals may retain the plugin.
        Thread thread=new Thread(null,action,"Slidev worker",0,false);
        thread.setDaemon(true);
        thread.setContextClassLoader(ClassLoader.getPlatformClassLoader());
        return thread;
    });
    public Future<?> submit(Runnable action) { return submit(Executors.callable(action)); }
    public synchronized <T> Future<T> submit(Callable<T> action) {
        FutureTask<T> task=new FutureTask<>(action) {
            @Override protected void done() { tasks.remove(this); }
        };
        tasks.add(task);
        try { workers.execute(task); }
        catch(RejectedExecutionException ex) { task.cancel(false);throw ex; }
        return task;
    }
    @Override public void execute(Runnable action) { submit(action); }
    @Override public synchronized void close() {
        tasks.forEach(task -> task.cancel(true));
        workers.shutdownNow();
    }
    /** For off-EDT tests only. Never wait for IDE read actions while unloading on the EDT. */
    public boolean awaitTermination(long timeout,TimeUnit unit)throws InterruptedException {
        return workers.awaitTermination(timeout,unit);
    }
}
