package ru.ashemchuk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Concurrent traversal with one blocking HTTP request per virtual thread. */
public final class HttpSpider {
    private final URI root;
    private final Duration requestTimeout;

    public HttpSpider(URI root, Duration requestTimeout) {
        if (root == null || !("http".equals(root.getScheme()) || "https".equals(root.getScheme()))
                || root.getHost() == null || root.getUserInfo() != null
                || !"/".equals(root.getPath()) || root.getQuery() != null || root.getFragment() != null) {
            throw new IllegalArgumentException("Base URL must be an HTTP(S) root, e.g. http://localhost:8080/");
        }
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("Request timeout must be positive");
        }
        this.root = root;
        this.requestTimeout = requestTimeout;
    }

    public List<String> crawl() throws IOException, InterruptedException {
        return crawl((completed, pending) -> { });
    }

    /** Reports completed nodes and outstanding requests on the calling thread. */
    public List<String> crawl(BiConsumer<Integer, Integer> progress) throws IOException, InterruptedException {
        return crawl(progress, event -> { });
    }

    public List<String> crawl(BiConsumer<Integer, Integer> progress, Consumer<String> log)
            throws IOException, InterruptedException {
        // Only the calling thread owns visited, messages and pending.
        var visited = new HashSet<URI>();
        var messages = new ArrayList<String>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
             var client = HttpClient.newBuilder().connectTimeout(requestTimeout)
                     .followRedirects(HttpClient.Redirect.NEVER).build()) {
            var completions = new ExecutorCompletionService<Node>(executor);
            var futures = new ArrayList<java.util.concurrent.Future<Node>>();
            visited.add(root);
            futures.add(completions.submit(() -> fetch(client, root)));
            int pending = 1;
            long nextReport = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            try {
                progress.accept(0, pending);
                log.accept("GET " + root);
                while (pending > 0) {
                    var completed = completions.poll(1, TimeUnit.SECONDS);
                    if (completed != null) {
                        Node node = completed.get();
                        pending--;
                        messages.add(node.message());
                        log.accept("HTTP 200 " + node.uri() + " за " + node.millis() + " мс"
                                + "; message=" + new com.google.gson.JsonPrimitive(node.message())
                                + "; successors=" + node.successors());
                        for (URI successor : node.successors()) {
                            if (visited.add(successor)) {
                                futures.add(completions.submit(() -> fetch(client, successor)));
                                pending++;
                                log.accept("GET " + successor);
                            } else {
                                log.accept("Пропуск повторного URL: " + successor);
                            }
                        }
                    }
                    if (pending == 0 || System.nanoTime() >= nextReport) {
                        progress.accept(messages.size(), pending);
                        nextReport = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                    }
                }
            } catch (ExecutionException e) {
                throw new IOException("Traversal failed: " + e.getCause().getMessage(), e.getCause());
            } finally {
                // Cancel outstanding requests before executor/client close waits for them.
                futures.forEach(future -> future.cancel(true));
            }
        }
        log.accept("Все ответы получены. Сортировка " + messages.size() + " сообщений...");
        messages.sort(String::compareTo);
        return List.copyOf(messages);
    }

    private Node fetch(HttpClient client, URI uri) throws IOException, InterruptedException {
        long start = System.nanoTime();
        var request = HttpRequest.newBuilder(uri).timeout(requestTimeout).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException(uri + ": HTTP " + response.statusCode());
        }
        try {
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String message = string(json.get("message"));
            var successors = new ArrayList<URI>();
            for (JsonElement element : json.getAsJsonArray("successors")) {
                String path = string(element);
                // Server paths are root-relative even when returned without a leading slash.
                URI relative = URI.create(path);
                if (path.isBlank() || relative.isAbsolute() || relative.getRawAuthority() != null
                        || relative.getQuery() != null || relative.getFragment() != null) {
                    throw new IllegalArgumentException("Invalid successor path: " + path);
                }
                successors.add(root.resolve(relative).normalize());
            }
            return new Node(uri, message, List.copyOf(successors),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        } catch (RuntimeException e) {
            throw new IOException(uri + ": invalid JSON response: " + e.getMessage(), e);
        }
    }

    private static String string(JsonElement element) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Expected a JSON string");
        }
        return element.getAsString();
    }

    private record Node(URI uri, String message, List<URI> successors, long millis) { }
}
