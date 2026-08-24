package fr.nivcoo.challenges.service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

final class ChallengeReadInvalidationPublisher implements AutoCloseable {

    private final Consumer<Runnable> dispatcher;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private boolean notificationPending;
    private volatile boolean closed;

    ChallengeReadInvalidationPublisher(Consumer<Runnable> dispatcher) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
    }

    void addListener(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        synchronized (this) {
            if (closed) return;
            listeners.addIfAbsent(listener);
        }
    }

    void removeListener(Runnable listener) {
        if (listener != null) listeners.remove(listener);
    }

    void invalidate() {
        synchronized (this) {
            if (closed || notificationPending || listeners.isEmpty()) return;
            notificationPending = true;
        }
        try {
            dispatcher.accept(this::publishPending);
        } catch (RuntimeException exception) {
            synchronized (this) {
                notificationPending = false;
            }
            throw exception;
        }
    }

    private void publishPending() {
        List<Runnable> snapshot;
        synchronized (this) {
            notificationPending = false;
            if (closed) return;
            snapshot = List.copyOf(listeners);
        }
        for (Runnable listener : snapshot) {
            if (closed) return;
            try {
                listener.run();
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        notificationPending = false;
        listeners.clear();
    }
}
