package fr.nivcoo.challenges.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChallengeReadInvalidationPublisherTest {

    @Test
    void coalescesInvalidationsAndKeepsCallbacksOutsideItsLock() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        ChallengeReadInvalidationPublisher publisher = new ChallengeReadInvalidationPublisher(tasks::add);
        AtomicInteger observedCalls = new AtomicInteger();
        List<Boolean> callbackLockStates = new ArrayList<>();
        AtomicInteger failingCalls = new AtomicInteger();
        Runnable failing = () -> {
            failingCalls.incrementAndGet();
            throw new IllegalStateException("listener failure");
        };
        Runnable observing = () -> {
            int call = observedCalls.incrementAndGet();
            callbackLockStates.add(Thread.holdsLock(publisher));
            if (call == 1) publisher.invalidate();
        };
        publisher.addListener(failing);
        publisher.addListener(failing);
        publisher.addListener(observing);

        publisher.invalidate();
        publisher.invalidate();
        publisher.invalidate();

        assertEquals(1, tasks.size());
        assertEquals(0, observedCalls.get());

        tasks.removeFirst().run();
        assertEquals(1, observedCalls.get());
        assertEquals(1, tasks.size());

        tasks.removeFirst().run();
        assertEquals(2, observedCalls.get());
        assertEquals(List.of(false, false), callbackLockStates);
        assertEquals(2, failingCalls.get());

        publisher.removeListener(observing);
        publisher.invalidate();
        tasks.removeFirst().run();
        assertEquals(2, observedCalls.get());
        assertEquals(3, failingCalls.get());
    }

    @Test
    void closeSuppressesPendingAndFutureNotifications() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        ChallengeReadInvalidationPublisher publisher = new ChallengeReadInvalidationPublisher(tasks::add);
        AtomicInteger calls = new AtomicInteger();
        Runnable listener = calls::incrementAndGet;

        publisher.invalidate();
        assertEquals(0, tasks.size());

        publisher.addListener(listener);
        publisher.invalidate();
        assertEquals(1, tasks.size());

        publisher.close();
        publisher.addListener(listener);
        publisher.invalidate();
        tasks.removeFirst().run();

        assertEquals(0, calls.get());
        assertEquals(0, tasks.size());
    }

    @Test
    void dispatcherFailureDoesNotLeaveThePublisherPending() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        AtomicInteger dispatches = new AtomicInteger();
        ChallengeReadInvalidationPublisher publisher = new ChallengeReadInvalidationPublisher(task -> {
            if (dispatches.getAndIncrement() == 0) throw new IllegalStateException("dispatcher unavailable");
            tasks.add(task);
        });
        AtomicInteger calls = new AtomicInteger();
        publisher.addListener(calls::incrementAndGet);

        assertThrows(IllegalStateException.class, publisher::invalidate);
        publisher.invalidate();
        assertEquals(1, tasks.size());

        tasks.removeFirst().run();
        assertEquals(1, calls.get());
    }
}
