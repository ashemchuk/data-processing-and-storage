package ru.ashemchuk;

import java.io.PrintStream;
import java.util.Comparator;
import java.util.Locale;
import java.util.StringJoiner;

final class Benchmark {
    private static final int SIZE = 24;
    private static final long BETWEEN = 10;
    private static final long INSIDE = 10;
    private static final long WARMUP = 300;

    static void run(String[] args, PrintStream output) throws InterruptedException {
        if (args.length > 3) {
            throw new IllegalArgumentException("--benchmark [duration_ms] [repeats]");
        }
        long duration = args.length > 1 ? Long.parseLong(args[1]) : 2000;
        int repeats = args.length > 2 ? Integer.parseInt(args[2]) : 3;
        if (duration < 1 || repeats < 1) {
            throw new IllegalArgumentException("Duration and repeats must be positive");
        }
        output.printf("%d элемента, задержки %d/%d мс, замер %d мс, прогрев %d мс, повторов: %d.%n%n",
                SIZE, BETWEEN, INSIDE, duration, WARMUP, repeats);
        output.println("| Список | Потоков | Шаги по повторам | Среднее шагов | Шагов/с | Верхняя оценка шагов |");
        output.println("|---|---:|---|---:|---:|---:|");
        for (int threads : new int[]{1, 2, 4}) {
            for (String implementation : new String[]{"node", "standard"}) {
                var steps = new StringJoiner(" / ");
                long totalSteps = 0;
                double totalMillis = 0;
                for (int repeat = 0; repeat < repeats; repeat++) {
                    Result result = measure(implementation, threads, duration);
                    steps.add(Long.toString(result.steps()));
                    totalSteps += result.steps();
                    totalMillis += result.elapsedMillis();
                }
                double windows = implementation.equals("standard") ? 1 : (SIZE + 1) / 3;
                double rateLimit = Math.min(1000.0 * threads / (BETWEEN + INSIDE),
                        1000.0 * windows / INSIDE);
                output.printf(Locale.ROOT, "| %s | %d | %s | %.1f | %.2f | %.0f |%n",
                        implementation, threads, steps, (double) totalSteps / repeats,
                        1000 * totalSteps / totalMillis, rateLimit * duration / 1000);
            }
        }
        output.println("\nОценка предполагает идеальное перекрытие внешних пауз без накладных расходов; "
                + "для стандартного списка дополнительно учитывается один общий монитор.");
    }

    private static Result measure(String implementation, int threads, long duration)
            throws InterruptedException {
        SortableList<Integer> list = Main.createList(implementation, Comparator.naturalOrder());
        // Sorted input still requires every comparison of every pass.
        for (int value = SIZE - 1; value >= 0; value--) {
            list.addFirst(value);
        }
        var sorters = new Sorters(list, threads, BETWEEN, INSIDE);
        long start;
        long before;
        try (sorters) {
            Thread.sleep(WARMUP);
            sorters.checkFailure();
            before = list.stepCount();
            start = System.nanoTime();
            Thread.sleep(duration);
        }
        long end = System.nanoTime();
        sorters.checkFailure();
        return new Result(list.stepCount() - before, (end - start) / 1_000_000.0);
    }

    private record Result(long steps, double elapsedMillis) { }
}
