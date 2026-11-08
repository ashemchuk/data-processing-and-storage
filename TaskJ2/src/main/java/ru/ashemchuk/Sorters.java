package ru.ashemchuk;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Owns the worker threads and joins all of them on close. */
final class Sorters implements AutoCloseable {
    private final List<Thread> threads = new ArrayList<>();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    Sorters(SortableList<?> list, int count, long betweenMillis, long insideMillis) {
        if (count < 1) {
            throw new IllegalArgumentException("Thread count must be positive");
        }
        var sorter = new Sorter(list, betweenMillis, insideMillis);
        for (int i = 0; i < count; i++) {
            var thread = new Thread(sorter, "sorter-" + (i + 1));
            thread.setUncaughtExceptionHandler((worker, error) -> {
                failure.compareAndSet(null, error);
                threads.forEach(Thread::interrupt);
            });
            threads.add(thread);
        }
        threads.forEach(Thread::start);
    }

    void checkFailure() {
        Throwable error = failure.get();
        if (error != null) {
            throw new IllegalStateException("Sorting worker failed", error);
        }
    }

    @Override
    public void close() {
        threads.forEach(Thread::interrupt);
        boolean interrupted = false;
        for (Thread thread : threads) {
            boolean joined = false;
            while (!joined) {
                try {
                    thread.join();
                    joined = true;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
