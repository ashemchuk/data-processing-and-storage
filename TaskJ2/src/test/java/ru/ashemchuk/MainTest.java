package ru.ashemchuk;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.List;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    @ParameterizedTest
    @ValueSource(strings = {"node", "standard"})
    void splitsLongLinesInOriginalOrderWithoutBreakingUnicode(String implementation) {
        SortableList<String> list = Main.createList(implementation, Comparator.naturalOrder());
        String first = "😀".repeat(80);
        Main.addStringParts(list, first + "я".repeat(80) + "end");

        assertEquals(List.of(first, "я".repeat(80), "end"), ListContractTest.values(list));
    }

    @ParameterizedTest
    @ValueSource(strings = {"node", "standard"})
    void printsOnEmptyLineAndStopsWorkersAtEof(String implementation) {
        var output = new ByteArrayOutputStream();
        Main.run(new String[]{"--list", implementation, "--inside", "0", "--between", "0"},
                new Scanner("hello\n\n"), new PrintStream(output));

        String text = output.toString();
        assertTrue(text.contains("Текущее состояние:"));
        assertTrue(text.contains("\nhello\n"));
        assertTrue(text.contains("Рабочие потоки остановлены."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"node", "standard"})
    void exitCommandStopsWithoutReadingFurtherInput(String implementation) {
        var output = new ByteArrayOutputStream();
        var input = new Scanner("hello\n:q\nunread\n");

        Main.run(new String[]{"--list", implementation}, input, new PrintStream(output));

        assertEquals("unread", input.nextLine());
        assertTrue(output.toString().contains("Рабочие потоки остановлены."));
    }
}
