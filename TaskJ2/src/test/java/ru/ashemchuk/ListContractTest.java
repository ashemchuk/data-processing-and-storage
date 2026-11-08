package ru.ashemchuk;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class ListContractTest {
    @ParameterizedTest
    @ValueSource(strings = {"node", "standard"})
    void addsToHeadAndPreservesDuplicatesAndEmptyStrings(String implementation) {
        SortableList<String> list = Main.createList(implementation, Comparator.naturalOrder());
        list.addFirst("same");
        list.addFirst("");
        list.addFirst("same");
        list.addFirst("last");

        assertEquals(List.of("last", "same", "", "same"), values(list));
    }

    @ParameterizedTest
    @ValueSource(strings = {"node", "standard"})
    void onePassMovesLargestElementToTail(String implementation) {
        var list = numbers(implementation, Comparator.naturalOrder(), 3, 2, 1);

        list.sortPass();

        assertEquals(List.of(2, 1, 3), values(list));
        assertEquals(2, list.stepCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"node", "standard"})
    void sortedListStillCountsAllComparisons(String implementation) {
        var list = numbers(implementation, Comparator.naturalOrder(), 1, 2, 3);

        list.sortPass();
        list.sortPass();

        assertEquals(List.of(1, 2, 3), values(list));
        assertEquals(4, list.stepCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"node", "standard"})
    void emptyAndSingleElementListsHaveNoSteps(String implementation) {
        var list = numbers(implementation, Comparator.naturalOrder());
        list.sortPass();
        assertTrue(values(list).isEmpty());
        assertThrows(NoSuchElementException.class, () -> list.iterator().next());
        list.addFirst(1);
        list.sortPass();

        assertEquals(List.of(1), values(list));
        assertEquals(0, list.stepCount());
    }

    @ParameterizedTest
    @ValueSource(strings = {"node", "standard"})
    void reverseComparatorAndDuplicatesAreSupported(String implementation) {
        var list = numbers(implementation, Comparator.reverseOrder(), 1, 2, 1, 3);
        for (int i = 0; i < 4; i++) {
            list.sortPass();
        }

        assertEquals(List.of(3, 2, 1, 1), values(list));
    }

    @ParameterizedTest
    @ValueSource(strings = {"node", "standard"})
    void iteratorIsAnIndependentSnapshot(String implementation) {
        var list = numbers(implementation, Comparator.naturalOrder(), 2, 1);
        var snapshot = list.iterator();

        list.addFirst(3);
        list.sortPass();

        assertEquals(2, snapshot.next());
        assertEquals(1, snapshot.next());
        assertFalse(snapshot.hasNext());
        assertThrows(NoSuchElementException.class, snapshot::next);
    }

    private static SortableList<Integer> numbers(String implementation,
                                                Comparator<Integer> comparator, int... values) {
        SortableList<Integer> list = Main.createList(implementation, comparator);
        for (int i = values.length - 1; i >= 0; i--) {
            list.addFirst(values[i]);
        }
        return list;
    }

    static <T> List<T> values(Iterable<T> list) {
        List<T> result = new ArrayList<>();
        for (T value : list) {
            result.add(value);
        }
        return result;
    }
}
