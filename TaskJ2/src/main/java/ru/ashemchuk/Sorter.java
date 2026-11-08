package ru.ashemchuk;

import java.util.Objects;

/** Repeats full passes even when the list is already sorted. */
public final class Sorter implements Runnable {
    private final SortableList<?> list;
    private final long betweenStepsMillis;
    private final long insideStepMillis;

    public Sorter(SortableList<?> list, long betweenStepsMillis, long insideStepMillis) {
        this.list = Objects.requireNonNull(list, "list");
        if (betweenStepsMillis < 0 || insideStepMillis < 0) {
            throw new IllegalArgumentException("Delays must be non-negative");
        }
        this.betweenStepsMillis = betweenStepsMillis;
        this.insideStepMillis = insideStepMillis;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                long before = list.stepCount();
                list.sortPass(betweenStepsMillis, insideStepMillis);
                // Avoid a busy loop with zero or one element.
                if (list.stepCount() == before) {
                    Thread.sleep(1);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
