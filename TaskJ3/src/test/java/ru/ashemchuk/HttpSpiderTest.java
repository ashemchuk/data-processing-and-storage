package ru.ashemchuk;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class HttpSpiderTest {
    private HttpServer server;
    private java.util.concurrent.ExecutorService executor;
    private final Map<String, String> bodies = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> requests = new ConcurrentHashMap<>();
    private volatile CountDownLatch siblings;
    private final AtomicBoolean concurrent = new AtomicBoolean(true);

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try (exchange) {
                String path = exchange.getRequestURI().getPath();
                requests.computeIfAbsent(path, ignored -> new AtomicInteger()).incrementAndGet();
                if (!path.equals("/") && siblings != null) {
                    siblings.countDown();
                    try {
                        if (!siblings.await(3, TimeUnit.SECONDS)) concurrent.set(false);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                String body = bodies.get(path);
                byte[] data = (body == null ? "Not found" : body).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(body == null ? 404 : 200, data.length);
                exchange.getResponseBody().write(data);
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    private HttpSpider spider(Duration timeout) {
        return new HttpSpider(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"), timeout);
    }

    @Test
    void traversesCyclesAndDiamondOnceAndPreservesDuplicateMessages() throws Exception {
        bodies.put("/", "{\"message\":\"я\",\"successors\":[\"a\",\"b\",\"a\"]}");
        bodies.put("/a", "{\"message\":\"same\",\"successors\":[\"c\",\"/\"]}");
        bodies.put("/b", "{\"message\":\"same\",\"successors\":[\"c\"]}");
        bodies.put("/c", "{\"message\":\"a\\n\\\"b\",\"successors\":[]}");
        assertEquals(List.of("a\n\"b", "same", "same", "я"), spider(Duration.ofSeconds(5)).crawl());
        assertEquals(4, requests.size());
        requests.values().forEach(count -> assertEquals(1, count.get()));
    }

    @Test
    void requestsSiblingsConcurrently() throws Exception {
        siblings = new CountDownLatch(2);
        bodies.put("/", "{\"message\":\"root\",\"successors\":[\"a\",\"b\"]}");
        bodies.put("/a", "{\"message\":\"a\",\"successors\":[]}");
        bodies.put("/b", "{\"message\":\"b\",\"successors\":[]}");
        assertEquals(3, spider(Duration.ofSeconds(5)).crawl().size());
        assertTrue(concurrent.get(), "Both requests must reach server before either is released");
    }

    @Test
    void propagatesHttpFailure() {
        bodies.put("/", "{\"message\":\"root\",\"successors\":[\"missing\"]}");
        assertTrue(assertThrows(IOException.class, () -> spider(Duration.ofSeconds(5)).crawl())
                .getMessage().contains("HTTP 404"));
    }

    @Test
    void rejectsMalformedResponseAndExternalSuccessors() {
        for (String body : List.of("not json", "{\"message\":3,\"successors\":[]}",
                "{\"message\":\"a\"}", "{\"message\":\"a\",\"successors\":[\"//example.com/a\"]}")) {
            bodies.put("/", body);
            assertThrows(IOException.class, () -> spider(Duration.ofSeconds(5)).crawl());
        }
    }

    @Test
    void timesOutBlockedRequests() {
        siblings = new CountDownLatch(2);
        bodies.put("/", "{\"message\":\"root\",\"successors\":[\"slow\"]}");
        bodies.put("/slow", "{\"message\":\"slow\",\"successors\":[]}");
        assertThrows(IOException.class, () -> spider(Duration.ofMillis(200)).crawl());
    }

    @Test
    void validatesConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new HttpSpider(URI.create("file:///"), Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> spider(Duration.ZERO));
    }
}
