package fr.nivcoo.challenges.challenges;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoalescingReadModelRefreshTest {

    @Test
    void coalescesRequestsUntilTheDeferredRefreshRuns() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        AtomicInteger refreshes = new AtomicInteger();
        CoalescingReadModelRefresh refresh = new CoalescingReadModelRefresh(tasks::add, refreshes::incrementAndGet);

        refresh.request();
        refresh.request();
        refresh.request();

        assertEquals(1, tasks.size());
        assertEquals(0, refreshes.get());
        tasks.removeFirst().run();
        assertEquals(1, refreshes.get());

        refresh.request();
        assertEquals(1, tasks.size());
        tasks.removeFirst().run();
        assertEquals(2, refreshes.get());
    }

    @Test
    void closeDropsQueuedAndFutureRefreshes() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        AtomicInteger refreshes = new AtomicInteger();
        CoalescingReadModelRefresh refresh = new CoalescingReadModelRefresh(tasks::add, refreshes::incrementAndGet);

        refresh.request();
        refresh.close();
        refresh.request();
        tasks.removeFirst().run();

        assertEquals(0, refreshes.get());
        assertEquals(0, tasks.size());
    }

    @Test
    void dispatcherFailureAllowsTheNextRequestToRetry() {
        ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        AtomicInteger dispatches = new AtomicInteger();
        CoalescingReadModelRefresh refresh = new CoalescingReadModelRefresh(task -> {
            if (dispatches.getAndIncrement() == 0) throw new IllegalStateException("dispatcher unavailable");
            tasks.add(task);
        }, () -> {
        });

        assertThrows(IllegalStateException.class, refresh::request);
        refresh.request();

        assertEquals(1, tasks.size());
    }
}
