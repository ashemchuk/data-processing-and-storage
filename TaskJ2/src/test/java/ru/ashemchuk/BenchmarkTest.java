package ru.ashemchuk;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkTest {
    @Test
    void printsMarkdownForBothListsAndAllThreadCounts() throws Exception {
        var output = new ByteArrayOutputStream();

        Benchmark.run(new String[]{"--benchmark", "1", "1"}, new PrintStream(output));

        String table = output.toString();
        assertTrue(table.contains("| Среднее шагов | Шагов/с |"));
        for (String implementation : new String[]{"node", "standard"}) {
            for (int threads : new int[]{1, 2, 4}) {
                assertTrue(table.contains("| " + implementation + " | " + threads + " |"));
            }
        }
    }

    @Test
    void rejectsNonPositiveDurationAndRepeatCount() {
        var output = new PrintStream(new ByteArrayOutputStream());

        assertThrows(IllegalArgumentException.class,
                () -> Benchmark.run(new String[]{"--benchmark", "0", "1"}, output));
        assertThrows(IllegalArgumentException.class,
                () -> Benchmark.run(new String[]{"--benchmark", "1", "0"}, output));
    }
}
