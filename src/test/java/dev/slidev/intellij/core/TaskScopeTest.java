package dev.slidev.intellij.core;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TaskScopeTest {
    private static int checks;
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message);checks++; }
    public static void main(String[] args)throws Exception {
        var scope=new TaskScope();
        var started=new CountDownLatch(1);var exited=new CountDownLatch(1);var interrupted=new AtomicBoolean();
        var inherited=new InheritableThreadLocal<Object>();inherited.set(new Object());
        try {
            check(scope.submit(()->Thread.currentThread().getContextClassLoader()).get()==ClassLoader.getPlatformClassLoader(),"Worker does not inherit plugin class loader");
            check(scope.submit(inherited::get).get()==null,"Worker does not inherit IDE thread locals");
            check(scope.submit(()->42).get()==42,"Results are available");
            var work=scope.submit(()->{
                started.countDown();
                try {new CountDownLatch(1).await();}
                catch(InterruptedException ex) {interrupted.set(true);Thread.currentThread().interrupt();}
                finally {exited.countDown();}
            });
            check(started.await(5,TimeUnit.SECONDS),"Work started");
            var cleaned=new CountDownLatch(1);
            scope.execute(cleaned::countDown);
            scope.close();scope.close();
            check(cleaned.await(5,TimeUnit.SECONDS),"Already scheduled cleanup is drained on unload");
            check(exited.await(5,TimeUnit.SECONDS) && interrupted.get(),"Unload interrupts running work");
            check(work.isCancelled(),"Running Future is cancelled");
            check(scope.awaitTermination(5,TimeUnit.SECONDS),"No worker remains after unload");
            try {scope.submit(()->{});throw new AssertionError("New work accepted after unload");}
            catch(RejectedExecutionException expected) {checks++;}
        } finally {inherited.remove();scope.close();}
        System.out.println("PASS: "+checks+" plugin task lifecycle checks");
    }
}
