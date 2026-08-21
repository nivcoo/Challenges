package fr.nivcoo.challenges.service;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class BoundedStorageExecutor implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicReference<Thread> workerThread = new AtomicReference<>();

    public BoundedStorageExecutor(String threadName, int queueCapacity) {
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            workerThread.set(thread);
            return thread;
        };
        executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity), factory, new ThreadPoolExecutor.AbortPolicy());
    }

    public <T> CompletableFuture<T> submit(Callable<T> operation) {
        CompletableFuture<T> future = new CompletableFuture<>();
        StorageTask<T> task = new StorageTask<>(operation, future);
        if (closed.get()) {
            task.reject(new RejectedExecutionException("Storage executor is closed."));
            return future;
        }
        try {
            executor.execute(task);
        } catch (RejectedExecutionException exception) {
            task.reject(exception);
        }
        return future;
    }

    public CompletableFuture<Void> submit(CheckedRunnable operation) {
        return submit(() -> {
            operation.run();
            return null;
        });
    }

    @Override
    public void close() {
        closeGracefully(30, TimeUnit.SECONDS);
    }

    public boolean closeGracefully(long timeout, TimeUnit unit) {
        if (timeout < 0) throw new IllegalArgumentException("timeout must be non-negative");
        if (unit == null) throw new IllegalArgumentException("unit is required");
        if (Thread.currentThread() == workerThread.get()) {
            throw new IllegalStateException("Storage executor cannot await its own termination.");
        }
        if (!closed.compareAndSet(false, true)) return executor.isTerminated();

        executor.shutdown();
        boolean terminated = awaitTermination(timeout, unit);
        if (terminated) return true;

        List<Runnable> abandoned = executor.shutdownNow();
        RejectedExecutionException failure = new RejectedExecutionException("Storage executor was stopped.");
        for (Runnable runnable : abandoned) {
            if (runnable instanceof StorageTask<?> task) task.reject(failure);
        }
        awaitTermination(Math.min(TimeUnit.SECONDS.toNanos(5), unit.toNanos(timeout)), TimeUnit.NANOSECONDS);
        return executor.isTerminated();
    }

    private boolean awaitTermination(long timeout, TimeUnit unit) {
        try {
            return executor.awaitTermination(timeout, unit);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @FunctionalInterface
    public interface CheckedRunnable {
        void run() throws Exception;
    }

    private record StorageTask<T>(Callable<T> operation, CompletableFuture<T> future) implements Runnable {
        @Override
        public void run() {
            if (future.isDone()) return;
            try {
                future.complete(operation.call());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        }

        private void reject(Throwable throwable) {
            future.completeExceptionally(throwable);
        }
    }
}
