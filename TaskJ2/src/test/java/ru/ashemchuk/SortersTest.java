package ru.ashemchuk;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Comparator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class SortersTest {
    @ParameterizedTest
    @ValueSource(strings = {"node", "standard"})
    void workersSortAndContinueComparingSortedList(String implementation) throws Exception {
        SortableList<Integer> list = Main.createList(implementation, Comparator.naturalOrder());
        for (int i = 0; i < 20; i++) {
            list.addFirst(i);
        }
        try (var workers = new Sorters(list, 3, 0, 0)) {
            await(() -> ListContractTest.values(list).equals(IntStream.range(0, 20).boxed().toList()));
            long sortedSteps = list.stepCount();
            await(() -> list.stepCount() > sortedSteps + 20);
            workers.checkFailure();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"node", "standard"})
    void concurrentInputAndSnapshotsPreserveEveryEntry(String implementation) throws Exception {
        SortableList<Integer> list = Main.createList(implementation, Comparator.naturalOrder());
        try (var workers = new Sorters(list, 3, 0, 0)) {
            for (int i = 0; i < 100; i++) {
                list.addFirst(i);
                var snapshot = ListContractTest.values(list);
                snapshot.sort(Comparator.naturalOrder());
                assertEquals(IntStream.rangeClosed(0, i).boxed().toList(), snapshot);
            }
            workers.checkFailure();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"node", "standard"})
    void closeInterruptsLongInsideDelayAndReleasesLocks(String implementation) throws Exception {
        var compared = new CountDownLatch(1);
        SortableList<Integer> list = Main.createList(implementation, (Integer a, Integer b) -> {
            compared.countDown();
            return Integer.compare(a, b);
        });
        list.addFirst(1);
        list.addFirst(2);
        try (var workers = new Sorters(list, 3, 0, 30_000)) {
            assertTrue(compared.await(2, TimeUnit.SECONDS));
            workers.checkFailure();
        }
        assertEquals(java.util.List.of(2, 1), ListContractTest.values(list));
        list.sortPass();
        assertEquals(java.util.List.of(1, 2), ListContractTest.values(list));
    }

    private static void await(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                fail("Workers did not make progress");
            }
            Thread.sleep(1);
        }
    }
}
