package fr.nivcoo.challenges.challenges;

import java.util.Objects;
import java.util.concurrent.Executor;

final class CoalescingReadModelRefresh implements AutoCloseable {

    private final Executor executor;
    private final Runnable refresh;
    private boolean pending;
    private boolean closed;

    CoalescingReadModelRefresh(Executor executor, Runnable refresh) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.refresh = Objects.requireNonNull(refresh, "refresh");
    }

    void request() {
        synchronized (this) {
            if (closed || pending) return;
            pending = true;
        }
        try {
            executor.execute(this::runPending);
        } catch (RuntimeException exception) {
            synchronized (this) {
                pending = false;
            }
            throw exception;
        }
    }

    private void runPending() {
        synchronized (this) {
            pending = false;
            if (closed) return;
        }
        refresh.run();
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        pending = false;
    }
}
