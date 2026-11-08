package ru.ashemchuk;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        try {
            if (args.length > 0 && args[0].equals("--benchmark")) {
                Benchmark.run(args, System.out);
            } else if (args.length == 1 && args[0].equals("--help")) {
                printHelp(System.out);
            } else {
                run(args, new Scanner(System.in), System.out);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.err.println("Ошибка: " + e.getMessage());
            System.exit(1);
        }
    }

    static void run(String[] args, Scanner input, PrintStream output) {
        String implementation = "node";
        int count = 2;
        long between = 1000;
        long inside = 1000;
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 == args.length) {
                throw new IllegalArgumentException("Нет значения для " + args[i]);
            }
            switch (args[i]) {
                case "--list" -> implementation = args[i + 1];
                case "--threads" -> count = Integer.parseInt(args[i + 1]);
                case "--between" -> between = Long.parseLong(args[i + 1]);
                case "--inside" -> inside = Long.parseLong(args[i + 1]);
                default -> throw new IllegalArgumentException("Неизвестный параметр: " + args[i]);
            }
        }
        SortableList<String> list = createList(implementation, Comparator.naturalOrder());
        var sorters = new Sorters(list, count, between, inside);
        try (sorters) {
            output.printf("Список: %s; потоков: %d; задержки: %d/%d мс.%n",
                    implementation, count, between, inside);
            output.println("Вводите строки. Пустая строка — вывод списка; :q или EOF — выход.");
            while (input.hasNextLine()) {
                sorters.checkFailure();
                String line = input.nextLine();
                if (line.equals(":q")) {
                    break;
                }
                if (line.isEmpty()) {
                    output.println("Текущее состояние:");
                    for (String value : list) {
                        output.println(value);
                    }
                    output.println("Завершённых шагов: " + list.stepCount());
                } else {
                    addStringParts(list, line);
                }
            }
            if (input.ioException() != null) {
                throw new IllegalStateException("Ошибка чтения ввода", input.ioException());
            }
            sorters.checkFailure();
        }
        sorters.checkFailure();
        output.println("Рабочие потоки остановлены. Всего шагов: " + list.stepCount());
    }

    static <T> SortableList<T> createList(String implementation, Comparator<? super T> comparator) {
        return switch (implementation) {
            case "node" -> new NodeLinkedList<>(comparator);
            case "standard" -> new StandartList<>(comparator);
            default -> throw new IllegalArgumentException("Тип списка: node или standard");
        };
    }

    static void addStringParts(SortableList<String> list, String line) {
        var parts = new ArrayList<String>();
        int remaining = line.codePointCount(0, line.length());
        int start = 0;
        while (remaining > 0) {
            int length = Math.min(80, remaining);
            int end = line.offsetByCodePoints(start, length);
            parts.add(line.substring(start, end));
            start = end;
            remaining -= length;
        }
        // Preserve the left-to-right order of the chunks at the head.
        for (int i = parts.size() - 1; i >= 0; i--) {
            list.addFirst(parts.get(i));
        }
    }

    private static void printHelp(PrintStream output) {
        output.println("--list node|standard --threads N --between MS --inside MS");
        output.println("По умолчанию: node, 2 потока, обе задержки по 1000 мс.");
        output.println("--benchmark [длительность_мс] [повторов]");
    }
}
