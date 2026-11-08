package ru.ashemchuk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public class StandartList<T> implements SortableList<T> {
    private final List<T> values = Collections.synchronizedList(new ArrayList<>());
    private final Comparator<? super T> comparator;
    private final AtomicLong steps = new AtomicLong();

    public StandartList(Comparator<? super T> comparator) {
        this.comparator = Objects.requireNonNull(comparator, "comparator");
    }

    @Override
    public void addFirst(T value) {
        values.add(0, value);
    }

    @Override
    public void sortPass(long betweenStepsMillis, long insideStepMillis)
            throws InterruptedException {
        if (betweenStepsMillis < 0 || insideStepMillis < 0) {
            throw new IllegalArgumentException("Delays must be non-negative");
        }
        int size = values.size();
        for (int index = 0; index < size - 1; index++) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            // Reading, comparing and swapping form one compound operation.
            synchronized (values) {
                int comparison = comparator.compare(values.get(index), values.get(index + 1));
                Thread.sleep(insideStepMillis);
                if (comparison > 0) {
                    Collections.swap(values, index, index + 1);
                }
                steps.incrementAndGet();
            }
            Thread.sleep(betweenStepsMillis);
        }
    }

    @Override
    public Iterator<T> iterator() {
        synchronized (values) {
            return new ArrayList<>(values).iterator();
        }
    }

    @Override
    public long stepCount() {
        return steps.get();
    }
}
