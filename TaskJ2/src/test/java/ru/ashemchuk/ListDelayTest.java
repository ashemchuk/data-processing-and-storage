package ru.ashemchuk;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ListDelayTest {

    @ParameterizedTest
    @ValueSource(strings = {"node", "standard"})
    void snapshotIsAvailableDuringDelayBetweenSteps(String implementation) throws Exception {
        var compared = new CountDownLatch(1);
        var list = listWithSignal(implementation, compared);
        var executor = newExecutor();
        try {
            var sorting = executor.submit(() -> {
                assertThrows(InterruptedException.class, () -> list.sortPass(30_000, 0));
            });
            assertTrue(compared.await(2, TimeUnit.SECONDS));

            var snapshot = executor.submit(() -> values(list));
            assertEquals(List.of(2, 3, 1), snapshot.get(2, TimeUnit.SECONDS));

            executor.shutdownNow();
            sorting.get(2, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    private static SortableList<Integer> listWithSignal(String implementation, CountDownLatch compared) {
        SortableList<Integer> list = Main.createList(implementation, (Integer a, Integer b) -> {
            compared.countDown();
            return Integer.compare(a, b);
        });
        list.addFirst(1);
        list.addFirst(2);
        list.addFirst(3);
        return list;
    }

    private static List<Integer> values(Iterable<Integer> list) {
        List<Integer> result = new ArrayList<>();
        for (Integer value : list) {
            result.add(value);
        }
        return result;
    }

    private static java.util.concurrent.ExecutorService newExecutor() {
        return Executors.newFixedThreadPool(2, task -> {
            var thread = new Thread(task);
            thread.setDaemon(true);
            return thread;
        });
    }
}
