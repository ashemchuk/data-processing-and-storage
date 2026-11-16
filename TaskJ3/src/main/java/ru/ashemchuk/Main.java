package ru.ashemchuk;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;

public final class Main {
    private Main() { }

    public static void main(String[] args) {
        if (args.length > 2 || (args.length > 0 && "--help".equals(args[0]))) {
            System.err.println("Usage: spider [http://localhost:8080/] [request-timeout-seconds=30]");
            if (args.length > 2) System.exit(2);
            return;
        }
        try {
            URI root = URI.create(args.length > 0 ? args[0] : "http://localhost:8080/");
            Duration timeout = Duration.ofSeconds(args.length > 1 ? Long.parseLong(args[1]) : 30);
            long start = System.nanoTime();
            System.err.println("Начинаем обход " + root);
            var messages = new HttpSpider(root, timeout).crawl((completed, pending) ->
                    System.err.printf("[%.0f с] Получено узлов: %d, ожидается ответов: %d%n",
                            (System.nanoTime() - start) / 1_000_000_000.0, completed, pending),
                    event -> System.err.printf("[%.1f с] %s%n",
                            (System.nanoTime() - start) / 1_000_000_000.0, event));
            messages.forEach(System.out::println);
            System.err.printf("Готово: %d сообщений за %.3f с%n", messages.size(),
                    (System.nanoTime() - start) / 1_000_000_000.0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Traversal interrupted");
            System.exit(1);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
