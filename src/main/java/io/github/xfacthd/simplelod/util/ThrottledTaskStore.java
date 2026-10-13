package io.github.xfacthd.simplelod.util;

import net.minecraft.util.Util;
import org.apache.commons.lang3.exception.UncheckedInterruptedException;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.UnaryOperator;

public final class ThrottledTaskStore {
    private final Executor throttleExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore throttleSemaphore;
    private final Set<TaskHandle> activeTasks = ConcurrentHashMap.newKeySet();

    public static ThrottledTaskStore halfExecutors() {
        return new ThrottledTaskStore(Math.max(Math.ceilDiv(Util.maxAllowedExecutorThreads(), 2), 1));
    }

    public static ThrottledTaskStore doubleExecutors() {
        return new ThrottledTaskStore(Util.maxAllowedExecutorThreads() * 2);
    }

    public static ThrottledTaskStore exact(int concurrency) {
        return new ThrottledTaskStore(concurrency);
    }

    private ThrottledTaskStore(int concurrency) {
        this.throttleSemaphore = new Semaphore(concurrency, true);
    }

    public void addTask(UnaryOperator<CompletableFuture<Void>> futureBuilder) {
        TaskHandle handle = new TaskHandle();
        activeTasks.add(handle);
        CompletableFuture<Void> rootFuture = CompletableFuture.runAsync(this::acquireTicket, throttleExecutor);
        handle.future = futureBuilder.apply(rootFuture);
        // Return value of CompletableFuture#handle() must not be captured in the handle to ensure its callback is called on cancel to release the semaphore
        handle.future.handle((_, _) -> finishTask(handle));
    }

    public void cancelTasks() {
        activeTasks.removeIf(task -> {
            task.future.cancel(true);
            return true;
        });
    }

    private void acquireTicket() {
        try {
            throttleSemaphore.acquire();
        } catch (InterruptedException e) {
            throw new UncheckedInterruptedException(e);
        }
    }

    private Void finishTask(TaskHandle handle) {
        activeTasks.remove(handle);
        throttleSemaphore.release();
        return null;
    }

    private static final class TaskHandle {
        private CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
    }
}
