package ru.ashemchuk;

import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import ru.ashemchuk.generation.CryptoGenerator;
import ru.ashemchuk.generation.CryptoIdentity;
import ru.ashemchuk.network.ResponseDecoder;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(20)
class ServerTest {
    private static CryptoIdentity identity;

    @BeforeAll
    static void setUp() throws Exception { identity = TestIdentities.create(); }

    @Test
    void servesOtherClientsWhileOneRequestIsIncomplete() throws Exception {
        try (RunningServer running = new RunningServer(name -> identity);
             Socket first = running.connect();
             Socket second = running.connect()) {
            send(first, "An");
            send(second, "Bob\0");
            assertIdentity(read(second));
            send(first, "na\0");
            assertIdentity(read(first));
        }
    }

    @Test
    void servesOneHundredWaitingClientsAndReusesResult() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<Socket> clients = new ArrayList<>();
        try (RunningServer running = new RunningServer(name -> {
            calls.incrementAndGet();
            started.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Test timed out");
            return identity;
        })) {
            try {
                for (int i = 0; i < 100; i++) {
                    Socket client = running.connect();
                    clients.add(client);
                    send(client, "Anna\0");
                }
                assertTrue(started.await(2, TimeUnit.SECONDS));
                release.countDown();
                for (Socket client : clients) assertIdentity(read(client));
                try (Socket repeated = running.connect()) {
                    send(repeated, "Anna\0");
                    assertIdentity(read(repeated));
                }
                assertEquals(1, calls.get());
            } finally {
                release.countDown();
                for (Socket client : clients) client.close();
            }
        }
    }

    @Test
    void servesFastClientWhileAnotherDoesNotRead() throws Exception {
        CryptoIdentity large = TestIdentities.withLargeCertificate(identity);
        CountDownLatch requested = new CountDownLatch(1);
        try (RunningServer running = new RunningServer(name -> {
            if (name.equals("slow")) {
                requested.countDown();
                return large;
            }
            return identity;
        }); Socket slow = running.connect()) {
            slow.setReceiveBufferSize(1024);
            send(slow, "slow\0");
            assertTrue(requested.await(2, TimeUnit.SECONDS));
            try (Socket fast = running.connect()) {
                send(fast, "Anna\0");
                assertIdentity(read(fast));
            }
        }
    }

    @Test
    void closesFailedRequestsAndContinuesServing() throws Exception {
        try (RunningServer running = new RunningServer(name -> {
            if (name.equals("fail")) throw new IOException("Expected failure");
            return identity;
        })) {
            try (Socket failed = running.connect()) {
                send(failed, "fail\0");
                assertEquals(-1, failed.getInputStream().read());
            }
            try (Socket invalid = running.connect()) {
                invalid.getOutputStream().write(new byte[] {(byte) 200});
                assertEquals(-1, invalid.getInputStream().read());
            }
            try (Socket disconnected = running.connect()) {
                disconnected.setSoLinger(true, 0);
                send(disconnected, "Anna\0");
            }
            try (Socket healthy = running.connect()) {
                send(healthy, "Anna\0");
                assertIdentity(read(healthy));
            }
        }
    }

    private static void send(Socket client, String text) throws IOException {
        client.getOutputStream().write(text.getBytes(StandardCharsets.US_ASCII));
    }

    private static CryptoIdentity read(Socket client) throws Exception {
        CryptoIdentity received = new ResponseDecoder().decode(client.getInputStream());
        assertEquals(-1, client.getInputStream().read(), "Server must finish the response");
        return received;
    }

    private static void assertIdentity(CryptoIdentity received) throws Exception {
        assertArrayEquals(identity.key().getEncoded(), received.key().getEncoded());
        assertArrayEquals(identity.cert().getEncoded(), received.cert().getEncoded());
    }

    private static final class RunningServer implements AutoCloseable {
        private final CryptoGenerator generator;
        private final Server server;
        private final ExecutorService thread = Executors.newSingleThreadExecutor();
        private final Future<?> task;

        RunningServer(CryptoGenerator.IdentityFactory factory) throws IOException {
            generator = new CryptoGenerator(2, factory);
            server = new Server(0, generator);
            task = thread.submit(() -> { server.start(); return null; });
        }

        Socket connect() throws IOException {
            Socket client = new Socket("localhost", server.getPort());
            client.setSoTimeout(5000);
            return client;
        }

        @Override
        public void close() throws Exception {
            server.close();
            try {
                task.get(5, TimeUnit.SECONDS);
            } finally {
                generator.close();
                thread.shutdownNow();
            }
        }
    }
}
