package ru.ashemchuk;

public interface SortableList<T> extends Iterable<T> {
    void addFirst(T value);

    default void sortPass() {
        try {
            sortPass(0, 0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Delays are in milliseconds; interruption aborts the current pass.
    void sortPass(long betweenStepsMillis, long insideStepMillis)
            throws InterruptedException;

    // Completed comparisons, including those without a swap.
    long stepCount();
}
